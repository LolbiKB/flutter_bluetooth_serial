package io.github.edufolly.flutterbluetoothserial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.Arrays;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;

/// Universal Bluetooth serial connection class (for Java)
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
            throw new IOException("Already connected");
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            throw new IOException("Device not found");
        }

        BluetoothSocket socket = null;
        try {
            socket = device.createRfcommSocketToServiceRecord(uuid);
            if (socket == null) {
                throw new IOException("Socket connection not established");
            }

            bluetoothAdapter.cancelDiscovery();
            socket.connect();
            connectionThread = new ConnectionThread(socket);
            connectionThread.start();
        } catch (IOException e) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    // Handle socket closing exception
                }
            }
            throw new IOException("Failed to connect", e);
        }
    }

    /// Connects to given device by hardware address (default UUID used)
    public void connect(String address) throws IOException {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = btAdapter.getRemoteDevice(address);
        ParcelUuid[] uuids = (ParcelUuid[]) device.getUuids();
        connect(address, uuids[0].getUuid());
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

        public ConnectionThread(BluetoothSocket socket) {
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
                } catch (IOException e) {
                    // Handle output stream closing exception
                }
            }
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    // Handle input stream closing exception
                }
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
            } catch (Exception e) {
                // Handle output flushing exception
            }
            if (socket != null) {
                try {
                    Thread.sleep(111);
                    socket.close();
                } catch (Exception e) {
                    // Handle socket closing exception
                }
            }
        }
    }
}
