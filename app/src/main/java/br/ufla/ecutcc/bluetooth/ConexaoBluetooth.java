package br.ufla.ecutcc.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Conexão serial pelo Bluetooth clássico (perfil SPP), que é o que o HC-06 oferece.
 * O pareamento (com o PIN do módulo) é feito antes, nas configurações do Android.
 * Quem chama precisa já ter a permissão de Bluetooth (ver MainActivity).
 */
@SuppressLint("MissingPermission")
public final class ConexaoBluetooth implements Closeable {

    /** UUID padrão do perfil de porta serial (SPP). */
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothSocket socket;

    private ConexaoBluetooth(BluetoothSocket socket) {
        this.socket = socket;
    }

    /** Bloqueia até conectar; chamar fora da thread da tela. */
    public static ConexaoBluetooth abrir(BluetoothDevice dispositivo) throws IOException {
        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
        BluetoothSocket socket = dispositivo.createRfcommSocketToServiceRecord(SPP);
        try {
            socket.connect();
        } catch (IOException erro) {
            socket.close();
            throw erro;
        }
        return new ConexaoBluetooth(socket);
    }

    public InputStream entrada() throws IOException {
        return socket.getInputStream();
    }

    public OutputStream saida() throws IOException {
        return socket.getOutputStream();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
