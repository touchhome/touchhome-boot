package org.touchhome.app.ble;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Controller;
import org.touchhome.app.TouchHomeBootSettings;
import org.touchhome.bundle.api.hardware.network.NetworkHardwareRepository;
import org.touchhome.bundle.api.hardware.other.MachineHardwareRepository;
import org.touchhome.bundle.bluetooth.BaseBluetoothCharacteristicService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@Controller
public class BluetoothBundleService extends BaseBluetoothCharacteristicService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File BOOT_SETTINGS_FILE = new File("~/.th_boot_settings");

    public BluetoothBundleService(MachineHardwareRepository machineHardwareRepository, NetworkHardwareRepository networkHardwareRepository) {
        super(machineHardwareRepository, networkHardwareRepository);
        init();
    }

    private TouchHomeBootSettings getSavedSettings() {
        try {
            return objectMapper.readValue(BOOT_SETTINGS_FILE, TouchHomeBootSettings.class);
        } catch (IOException e) {
            return new TouchHomeBootSettings();
        }
    }

    @SneakyThrows
    private void saveSettings(TouchHomeBootSettings touchHomeBootSettings) {
        objectMapper.writeValue(BOOT_SETTINGS_FILE, touchHomeBootSettings);
    }

    @Override
    public void writePwd(String loginUser, String pwd, String prevPwd) {
        TouchHomeBootSettings touchHomeBootSettings = getSavedSettings();
        touchHomeBootSettings.setUser(loginUser);
        touchHomeBootSettings.setPassword(pwd);
        saveSettings(touchHomeBootSettings);
    }

    @Override
    public String readPwdSet() {
        return "ok:0";
    }

    @Override
    public String readServerConnected() {
        return "unavailable";
    }

    @Override
    public boolean hasExtraAccess() {
        return true;
    }

    @Override
    public String getFeatures() {
        return "BOOT~~~true";
    }

    @Override
    public String getKeystore() {
        return "unavailable";
    }

    @Override
    public void updateBluetoothStatus(String status) {

    }

    @Override
    public void setFeatureState(boolean status) {

    }
}
