package org.touchhome.app.ble;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Controller;
import org.touchhome.bundle.api.hardware.network.NetworkHardwareRepository;
import org.touchhome.bundle.api.hardware.other.MachineHardwareRepository;
import org.touchhome.bundle.bluetooth.BaseBluetoothCharacteristicService;

@Log4j2
@Controller
public class BluetoothBundleService extends BaseBluetoothCharacteristicService {

    public BluetoothBundleService(MachineHardwareRepository machineHardwareRepository, NetworkHardwareRepository networkHardwareRepository) {
        super(machineHardwareRepository, networkHardwareRepository);
        init();
    }

    @Override
    public String readServerConnected() {
        return "unavailable";
    }

    @Override
    public String getFeatures() {
        return "";
    }

    @Override
    public void updateBluetoothStatus(String status) {

    }

    @Override
    public void setFeatureState(boolean status) {

    }
}
