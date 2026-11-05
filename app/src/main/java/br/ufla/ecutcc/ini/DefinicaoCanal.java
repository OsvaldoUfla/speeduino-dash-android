package br.ufla.ecutcc.ini;

/**
 * Um canal "scalar" da seção [OutputChannels] do .ini, por exemplo:
 * <pre>rpm = scalar, U16, 14, "rpm", 1.000, 0.000</pre>
 * Valor de usuário = (bruto + translate) * scale, como no TunerStudio.
 */
public final class DefinicaoCanal {
    public final String nome;
    public final String tipo;      // U08, S08, U16, S16, U32, S32
    public final int posicao;      // byte inicial dentro do bloco de dados
    public final double escala;
    public final double translate;

    public DefinicaoCanal(String nome, String tipo, int posicao, double escala, double translate) {
        this.nome = nome;
        this.tipo = tipo;
        this.posicao = posicao;
        this.escala = escala;
        this.translate = translate;
    }

    public int tamanho() {
        return Integer.parseInt(tipo.substring(1)) / 8;
    }

    /** Lê o canal do bloco recebido da ECU (little-endian, conforme o .ini). */
    public double ler(byte[] bloco) {
        int n = tamanho();
        if (posicao + n > bloco.length) {
            return Double.NaN;
        }
        long bruto = 0;
        for (int i = n - 1; i >= 0; i--) {
            bruto = (bruto << 8) | (bloco[posicao + i] & 0xFF);
        }
        if (tipo.charAt(0) == 'S') {
            int bits = n * 8;
            if ((bruto & (1L << (bits - 1))) != 0) {
                bruto -= (1L << bits);
            }
        }
        return (bruto + translate) * escala;
    }
}
