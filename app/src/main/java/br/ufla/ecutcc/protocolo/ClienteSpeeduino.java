package br.ufla.ecutcc.protocolo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.CRC32;

/**
 * Conversa com a ECU no mesmo protocolo do TunerStudio (firmware Speeduino 2025.01,
 * modo "Tuner Studio" da Serial3). Conferido em comms.cpp do firmware 202501.1.
 *
 * Toda mensagem, nos dois sentidos, vai num envelope:
 *   [tamanho: 2 bytes, big-endian] [conteúdo] [CRC32 do conteúdo: 4 bytes, big-endian]
 *
 * Comandos usados:
 *   'Q'  -> assinatura ("speeduino 202501")
 *   'S'  -> nome do produto com a versão completa ("Speeduino 2025.01.6")
 *   'r' 0x00 0x30 [posição: 2 bytes LE] [quantidade: 2 bytes LE] -> dados ao vivo
 * Nas respostas, o 1º byte do conteúdo é o código de retorno (0x00 = OK).
 */
public final class ClienteSpeeduino {

    private static final byte RC_OK = 0x00;
    private static final byte CMD_CANAIS_SAIDA = 0x30;
    private static final long TIMEOUT_MS = 2000; // blockReadTimeout do .ini

    private final InputStream entrada;
    private final OutputStream saida;

    public ClienteSpeeduino(InputStream entrada, OutputStream saida) {
        this.entrada = entrada;
        this.saida = saida;
    }

    public String lerAssinatura() throws IOException {
        return texto(trocar(new byte[]{'Q'}));
    }

    public String lerProduto() throws IOException {
        return texto(trocar(new byte[]{'S'}));
    }

    /** Lê 'quantidade' bytes do bloco de dados ao vivo, a partir de 'posicao'. */
    public byte[] lerCanais(int posicao, int quantidade) throws IOException {
        byte[] pedido = {
                'r', 0x00, CMD_CANAIS_SAIDA,
                (byte) (posicao & 0xFF), (byte) (posicao >> 8),
                (byte) (quantidade & 0xFF), (byte) (quantidade >> 8)
        };
        byte[] resposta = trocar(pedido);
        if (resposta.length < quantidade + 1 || resposta[0] != RC_OK) {
            throw new IOException("Resposta inesperada da ECU (código "
                    + (resposta.length > 0 ? resposta[0] & 0xFF : -1) + ")");
        }
        byte[] dados = new byte[quantidade];
        System.arraycopy(resposta, 1, dados, 0, quantidade);
        return dados;
    }

    private synchronized byte[] trocar(byte[] conteudo) throws IOException {
        while (entrada.available() > 0) {
            entrada.read(); // descarta restos de uma resposta anterior
        }
        CRC32 crc = new CRC32();
        crc.update(conteudo);
        byte[] pacote = new byte[2 + conteudo.length + 4];
        escreverBigEndian(pacote, 0, conteudo.length, 2);
        System.arraycopy(conteudo, 0, pacote, 2, conteudo.length);
        escreverBigEndian(pacote, 2 + conteudo.length, crc.getValue(), 4);
        saida.write(pacote);
        saida.flush();

        long limite = System.currentTimeMillis() + TIMEOUT_MS;
        int tamanho = (int) lerBigEndian(lerExato(2, limite));
        byte[] resposta = lerExato(tamanho, limite);
        long crcRecebido = lerBigEndian(lerExato(4, limite));
        CRC32 conferencia = new CRC32();
        conferencia.update(resposta);
        if (conferencia.getValue() != crcRecebido) {
            throw new IOException("CRC inválido na resposta da ECU");
        }
        return resposta;
    }

    private byte[] lerExato(int n, long limite) throws IOException {
        byte[] dados = new byte[n];
        int lidos = 0;
        while (lidos < n) {
            if (System.currentTimeMillis() > limite) {
                throw new IOException("A ECU não respondeu a tempo");
            }
            int r = entrada.read(dados, lidos, n - lidos);
            if (r < 0) {
                throw new IOException("Conexão encerrada");
            }
            lidos += r;
        }
        return dados;
    }

    private static void escreverBigEndian(byte[] destino, int inicio, long valor, int bytes) {
        for (int i = bytes - 1; i >= 0; i--) {
            destino[inicio + i] = (byte) (valor & 0xFF);
            valor >>>= 8;
        }
    }

    private static long lerBigEndian(byte[] dados) {
        long valor = 0;
        for (byte b : dados) {
            valor = (valor << 8) | (b & 0xFF);
        }
        return valor;
    }

    /** Tira o código de retorno (0x00) do início, se houver, e devolve o texto. */
    private static String texto(byte[] resposta) {
        int inicio = (resposta.length > 0 && resposta[0] == RC_OK) ? 1 : 0;
        return new String(resposta, inicio, resposta.length - inicio,
                Charset.forName("US-ASCII")).trim();
    }
}
