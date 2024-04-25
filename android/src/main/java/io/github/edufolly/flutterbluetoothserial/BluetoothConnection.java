import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.Arrays;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

public abstract class BluetoothConnection {

    protected static final UUID DEFAULT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    protected BluetoothAdapter bluetoothAdapter;

    protected ConnectionThread connectionThread = null;

    public boolean isConnected() {
        return connectionThread != null && connectionThread.requestedClosing != true;
    }

    public BluetoothConnection(BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public void connect(String address, UUID uuid) throws IOException {
        if (isConnected()) {
            throw new IOException("already connected");
        }
    
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            throw new IOException("device not found");
        }
    
        BluetoothSocket socket = null;
    
        try {
            Method m = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
            socket = (BluetoothSocket) m.invoke(device, 1);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("socket creation failed");
        }
    
        if (socket == null) {
            throw new IOException("socket connection not established");
        }
    
        bluetoothAdapter.cancelDiscovery();
    
        try {
            socket.connect();
        } catch (IOException e) {
            e.printStackTrace();
            try {
                socket = fallbackConnect(device);
            } catch (IOException | FallbackException e1) {
                e1.printStackTrace();
                throw new IOException("fallback connection failed");
            }
        }
    
        connectionThread = new ConnectionThread(socket);
        connectionThread.start();
    }

    private BluetoothSocket fallbackConnect(BluetoothDevice device) throws IOException, FallbackException {
        BluetoothSocket fallbackSocket = null;
        try {
            Method m = device.getClass().getMethod("createRfcommSocket", int.class);
            fallbackSocket = (BluetoothSocket) m.invoke(device, 1);
        } catch (Exception e) {
            throw new FallbackException(e);
        }
        if (fallbackSocket == null) {
            throw new FallbackException(new IOException("fallback socket creation failed"));
        }
        return fallbackSocket;
    }

    // Other methods remain unchanged...
}
