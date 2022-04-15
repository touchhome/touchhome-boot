package org.touchhome.app.rest;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.touchhome.app.ble.BluetoothBundleService;
import org.touchhome.app.ble.WebSocketConfig;
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.exception.ServerException;
import org.touchhome.common.model.ProgressBar;
import org.touchhome.common.util.CommonUtils;
import org.touchhome.common.util.Curl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@RestController
@RequestMapping("/rest")
@RequiredArgsConstructor
public class MainController {

    private final BluetoothBundleService bluetoothBundleService;
    private final SimpMessagingTemplate messagingTemplate;
    private boolean installingApp;

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

    @PostMapping("/app/downloadAndInstall")
    public void downloadAndInstall(@RequestBody DownloadAndInstallRequest request) {
        if (installingApp) {
            throw new ServerException("App already installing...");
        }
        try {
            this.installingApp = true;
            log.info("Installing application...");
            GitHubDescription gitHubDescription = Curl.get("https://api.github.com/repos/touchhome/touchhome-core/releases/latest", GitHubDescription.class);
            GitHubDescription.Asset asset = gitHubDescription.assets.stream().filter(a -> a.name.equals("touchhome.jar")).findAny().orElse(null);
            if (asset == null) {
                throw new NotFoundException("Unable to find touchhome.jar asset from server");
            }
            Path targetPath = CommonUtils.getRootPath().resolve("touchhome.jar");
            ProgressBar progressBar = (progress, message) ->
                    messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + "-global", new Progress(progress, message));
            Curl.downloadWithProgress(asset.browser_download_url, targetPath, progressBar);
        } finally {
            installingApp = false;
        }
    }

    private static class DownloadAndInstallRequest {
        private String email;
        private String password;
    }

    @Getter
    @AllArgsConstructor
    private static class Progress {
        private final String type = "progress";
        private final String specifier = "thd";
        private double value;
        private String title;
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
}
