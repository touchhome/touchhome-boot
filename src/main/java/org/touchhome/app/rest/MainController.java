package org.touchhome.app.rest;

import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.ble.BluetoothBundleService;
import org.touchhome.app.ble.WebSocketConfig;
import org.touchhome.app.hardware.StartupHardwareRepository;
import org.touchhome.bundle.api.hardware.other.MachineHardwareRepository;
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.exception.ServerException;
import org.touchhome.common.model.ProgressBar;
import org.touchhome.common.util.CommonUtils;
import org.touchhome.common.util.Curl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.touchhome.common.util.CommonUtils.OBJECT_MAPPER;

@Log4j2
@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
public class MainController {

    private final BluetoothBundleService bluetoothBundleService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MachineHardwareRepository machineHardwareRepository;
    private final StartupHardwareRepository startupHardwareRepository;
    private boolean installingApp;
    private boolean initInstalling;

    @GetMapping("/auth/status")
    public int getStatus() {
        return 407;
    }

    @GetMapping("/device/characteristic/{uuid}")
    public OptionModel getDeviceCharacteristic(@PathVariable("uuid") String uuid) {
        return OptionModel.key(bluetoothBundleService.getDeviceCharacteristic(uuid));
    }

    @PutMapping("/device/characteristic/{uuid}")
    public void setDeviceCharacteristic(@PathVariable("uuid") String uuid, @RequestBody byte[] value) {
        bluetoothBundleService.setDeviceCharacteristic(uuid, value);
    }

    @SneakyThrows
    @PostMapping("/app/config/user")
    public void setUserPassword(@RequestBody UserPasswordRequest request) {
        Files.write(CommonUtils.getRootPath().resolve("user_password.conf"), OBJECT_MAPPER.writeValueAsBytes(request),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @SneakyThrows
    @PostMapping("/app/config/finish")
    public void finishConfiguration() {
        log.info("Update /etc/systemd/system/touchhome.service");
        machineHardwareRepository.execute("sed -i 's/boot/core/g' /etc/systemd/system/touchhome.service");
        machineHardwareRepository.execute("sed -i '3 i After=postgresql.service' /etc/systemd/system/touchhome.service");
        machineHardwareRepository.execute("sed -i '4 i Requires=postgresql.service' /etc/systemd/system/touchhome.service");
        machineHardwareRepository.reboot();
    }

    @GetMapping("/app/config")
    public DeviceConfig getConfiguration() throws IOException {
        DeviceConfig deviceConfig = new DeviceConfig();
        deviceConfig.hasApp = Files.exists(CommonUtils.getRootPath().resolve("touchhome-core.jar"));
        deviceConfig.installingApp = this.installingApp;
        deviceConfig.initInstalling = this.initInstalling;
        Path prvKey = CommonUtils.getRootPath().resolve("init_private_key");
        deviceConfig.hasKeystore = Files.exists(prvKey);
        deviceConfig.hasInitSetup = isInitSetupDone();
        deviceConfig.keystoreDate = deviceConfig.hasKeystore ? new Date(Files.getLastModifiedTime(prvKey).toMillis()) : null;
        deviceConfig.hasUserPassword = Files.exists(CommonUtils.getRootPath().resolve("user_password.conf"));
        return deviceConfig;
    }

    @PostMapping("/app/config/init")
    public void initialSetup() {
        if (initInstalling) {
            throw new ServerException("Already installing...");
        }
        initInstalling = true;
        new Thread(() -> {
            try {
                ProgressBar progressBar = (progress, message) ->
                        messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + "-global", new Progress(Progress.Type.init, progress, message));
                try {
                    if (!isInitSetupDone()) {
                        progressBar.progress(5, "Update os");
                        machineHardwareRepository.execute("apt-get update", 600, progressBar);
                        progressBar.progress(20, "Full upgrade os");
                        machineHardwareRepository.execute("apt-get -y full-upgrade", 1200, progressBar);
                        machineHardwareRepository.installSoftware("autossh", 60);
                        progressBar.progress(30, "Install ffmpeg");
                        machineHardwareRepository.installSoftware("ffmpeg", 600);
                        progressBar.progress(40, "Installing Postgresql");
                        installPostgreSql(progressBar);
                        machineHardwareRepository.execute("apt-get clean");
                    }
                    progressBar.progress(100, "Done.");
                } catch (Exception ex) {
                    progressBar.progress(100, "Error: " + CommonUtils.getErrorMessage(ex));
                    throw new ServerException(ex);
                }
            } finally {
                initInstalling = false;
            }
        }).start();
    }

    private boolean isInitSetupDone() {
        return machineHardwareRepository.isSoftwareInstalled("psql");
    }

    private void installPostgreSql(ProgressBar progressBar) {
        machineHardwareRepository.installSoftware("postgresql", 1200, progressBar);
        String postgresPath = machineHardwareRepository.execute("find /usr -wholename '*/bin/postgres'");
        String version = Paths.get(postgresPath).subpath(3, 4).toString();
        for (String config : CommonUtils.readFile("configurePostgresql.conf")) {
            config = config.replace("$PSQL_CONF_PATH", "/etc/postgresql/" + version + "/main");
            machineHardwareRepository.execute(config);
        }
        if (!startupHardwareRepository.isPostgreSQLRunning()) {
            throw new ServerException("Postgresql is not running");
        }
        machineHardwareRepository.execute("sudo -u postgres psql -c \"ALTER user postgres WITH PASSWORD 'password'\"");
        machineHardwareRepository.execute("sudo -u postgres psql -c \"CREATE ROLE replication WITH REPLICATION PASSWORD 'password' LOGIN\"");
    }

    @SneakyThrows
    @PostMapping("/app/config/keystore")
    public void setKeystore(@RequestBody KeyStoreRequest keyStoreRequest) {
        Path ssh = CommonUtils.getRootPath().resolve("ssh");
        Files.write(ssh.resolve("id_rsa_touchhome"), keyStoreRequest.getPrvKey(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.write(ssh.resolve("id_rsa_touchhome.pub"), keyStoreRequest.getPubKey(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.write(CommonUtils.getRootPath().resolve("init_private_key"), keyStoreRequest.getKs(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @SneakyThrows
    @PostMapping("/app/config/downloadApp")
    public void downloadApp() {
        if (installingApp) {
            throw new ServerException("App already installing...");
        }
        ProgressBar progressBar = (progress, message) ->
                messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + "-global", new Progress(Progress.Type.download, progress, message));
        Path targetPath = CommonUtils.getRootPath().resolve("touchhome-core.jar");
        if (Files.exists(targetPath)) {
            progressBar.progress(100D, "App already downloaded.");
            return;
        }
        Path tmpPath = CommonUtils.getRootPath().resolve("touchhome-core_tmp.jar");
        try {
            this.installingApp = true;
            Files.deleteIfExists(tmpPath);

            log.info("Installing application...");
            GitHubDescription gitHubDescription = Curl.get("https://api.github.com/repos/touchhome/touchhome-core/releases/latest", GitHubDescription.class);

            String md5HashValue = getExpectedMD5Hash(gitHubDescription);

            GitHubDescription.Asset asset = gitHubDescription.assets.stream().filter(a -> a.name.equals("touchhome.jar")).findAny().orElse(null);
            if (asset == null) {
                throw new NotFoundException("Unable to find touchhome-code.jar asset from server");
            }
            log.info("Downloading touchhome.jar to <{}>", tmpPath);
            Curl.downloadWithProgress(asset.browser_download_url, tmpPath, progressBar);

            // test downloaded file md5 hash
            if (!md5HashValue.equals(DigestUtils.md5Hex(Files.newInputStream(tmpPath)))) {
                Files.delete(tmpPath);
                throw new ServerException("Downloaded file corrupted");
            }
            Files.move(tmpPath, targetPath);
            log.info("App installation finished");
        } finally {
            installingApp = false;
        }
    }

    private String getExpectedMD5Hash(GitHubDescription gitHubDescription) {
        GitHubDescription.Asset md5Asset = gitHubDescription.assets.stream().filter(a -> a.name.equals("md5.hex")).findAny().orElse(null);
        if (md5Asset == null) {
            throw new NotFoundException("Unable to find md5.hex asset from server");
        }
        return new String(Curl.download(md5Asset.browser_download_url).getBytes());
    }

    @Getter
    @Setter
    private static class DeviceConfig {
        private final boolean bootOnly = true;
        public boolean initInstalling;
        private boolean hasInitSetup;
        private boolean hasUserPassword;
        private boolean installingApp;
        private boolean hasKeystore;
        private Date keystoreDate;
        private boolean hasApp;
    }

    @Getter
    @Setter
    private static class UserPasswordRequest {
        private String email;
        private String password;
    }

    @Getter
    @AllArgsConstructor
    private static class Progress {
        private final Type type;
        private double value;
        private String title;

        private enum Type {
            download, init
        }
    }

    @Setter
    @Getter
    private static class GitHubDescription {
        private String name;
        private String tag_name;
        private List<Asset> assets = new ArrayList<>();

        @Setter
        @Getter
        private static class Asset {
            private String name;
            private long size;
            private String browser_download_url;
            private String updated_at;
        }
    }

    @Getter
    @Setter
    private static class KeyStoreRequest {
        private byte[] ks;
        private byte[] prvKey;
        private byte[] pubKey;
    }

/*
    # IN CASE OF sub:
    # export PRIMARY_IP=192.168.0.110
    # sudo systemctl stop postgresql
    # sudo -H -u postgres bash -c 'rm -rf $PSQL_DATA_PATH/main/*'
    # sudo PGPASSWORD="password" -H -u postgres bash -c "pg_basebackup -h $PRIMARY_IP -D /usr/local/pgsql/data -P -U replicator --xlog-method=stream"
    # sudo sed -i "s/#hot_standby = 'off'/hot_standby = 'on'/g" $PSQL_CONF_PATH/postgresql.conf
    # echo "standby_mode = 'on'\nprimary_conninfo = 'host=$PRIMARY_IP port=5432 user=replicator password=password'\ntrigger_file = '/var/lib/postgresql/9.6/trigger'\nrestore_command = 'cp /var/lib/postgresql/9.6/archive/%f \"%p\"'" >> $PSQL_DATA_PATH/main/recovery.conf
    # sudo systemctl start postgresql
*/
}
