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
        // O app não faz busca de dispositivos (usa os já pareados), então cancelar a busca
        // é só precaução. A partir do Android 12, cancelDiscovery() exige BLUETOOTH_SCAN,
        // que o app não pede: sem esse try, o app fechava ao tocar em Conectar (22/11/2026).
        try {
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
        } catch (SecurityException semPermissaoDeBusca) {
            // ignorado de propósito
        }
        // Módulos HC-05/HC-06 às vezes recusam o socket "seguro" pelo UUID (erro
        // "read failed, socket might closed"). Tenta três jeitos, do padrão ao alternativo.
        IOException ultimoErro = null;
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            BluetoothSocket socket;
            try {
                socket = criarSocket(dispositivo, tentativa);
            } catch (Exception semEsseJeito) {
                continue;
            }
            try {
                socket.connect();
                return new ConexaoBluetooth(socket);
            } catch (IOException erro) {
                ultimoErro = erro;
                try {
                    socket.close();
                } catch (IOException ignorado) {
                    // já estava fechado
                }
            }
        }
        throw ultimoErro != null ? ultimoErro : new IOException("não foi possível abrir o Bluetooth");
    }

    private static BluetoothSocket criarSocket(BluetoothDevice d, int tentativa) throws Exception {
        switch (tentativa) {
            case 0:
                return d.createRfcommSocketToServiceRecord(SPP);
            case 1:
                return d.createInsecureRfcommSocketToServiceRecord(SPP);
            default:
                // Canal RFCOMM 1 direto (método oculto, mas é o que funciona em muitos HC-06).
                return (BluetoothSocket) d.getClass()
                        .getMethod("createRfcommSocket", int.class)
                        .invoke(d, 1);
        }
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
