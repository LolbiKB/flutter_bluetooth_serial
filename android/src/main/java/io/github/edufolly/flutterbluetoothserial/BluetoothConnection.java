import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        return connectionThread != null && !connectionThread.requestedClosing;
    }

    public BluetoothConnection(BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public void connect(String address, UUID uuid) throws IOException {
        if (isConnected()) {
            throw new IOException("Already connected");
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            throw new IOException("Device not found");
        }

        BluetoothSocket socket = null;

        try {
            socket = device.createRfcommSocketToServiceRecord(uuid);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            socket.connect();
        } catch (IOException e) {
            try {
                socket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", int.class).invoke(device, 1);
                socket.connect();
                bluetoothAdapter.cancelDiscovery();
                connectionThread = new ConnectionThread(socket);
                connectionThread.start();               
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
    }

    public void connect(String address) throws IOException {
        connect(address, DEFAULT_UUID);
    }

    public void disconnect() {
        if (isConnected()) {
            connectionThread.cancel();
            connectionThread = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected");
        }
        
        connectionThread.write(data);
    }

    protected abstract void onRead(byte[] data);

    protected abstract void onDisconnected(boolean byRemote);

    private class ConnectionThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream input;
        private final OutputStream output;
        private boolean requestedClosing = false;
        
        ConnectionThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream(); 
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.input = tmpIn;
            this.output = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (!requestedClosing) {
                try {
                    bytes = input.read(buffer);
                    onRead(Arrays.copyOf(buffer, bytes));
                } catch (IOException e) {
                     break;
                }
            }

            if (output != null) {
                try {
                    output.close();
                } catch (Exception e) {}
            }

            if (input != null) {
                try {
                    input.close();
                } catch (Exception e) {}
            }
            
            onDisconnected(!requestedClosing);
            requestedClosing = true;
        }

        public void write(byte[] bytes) {
            try {
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cancel() {
            if (requestedClosing) {
                return;
            }
            requestedClosing = true;

            try {
                output.flush();
            } catch (Exception e) {}

            if (socket != null) {
                try {
                    Thread.sleep(111);
                    socket.close();
                } catch (Exception e) {}
            }
        }
    }
}
