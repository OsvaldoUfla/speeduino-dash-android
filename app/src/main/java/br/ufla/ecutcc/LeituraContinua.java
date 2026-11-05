package br.ufla.ecutcc;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import br.ufla.ecutcc.ini.DefinicaoCanal;
import br.ufla.ecutcc.ini.DefinicaoIni;
import br.ufla.ecutcc.modelo.Grandeza;
import br.ufla.ecutcc.protocolo.ClienteSpeeduino;

/**
 * Laço de leitura (thread própria): pede o bloco de dados ao vivo, converte cada
 * grandeza pela definição do .ini e entrega os valores à tela.
 */
final class LeituraContinua implements Runnable {

    interface Ouvinte {
        void aoLer(Map<Grandeza, Double> valores);
        void aoFalhar(String mensagem);
    }

    /** ~10 atualizações por segundo; o bloco de 130 bytes leva ~15 ms a 115200 baud. */
    private static final long INTERVALO_MS = 100;

    private final ClienteSpeeduino cliente;
    private final DefinicaoIni ini;
    private final Ouvinte ouvinte;
    private volatile boolean ativo = true;

    LeituraContinua(ClienteSpeeduino cliente, DefinicaoIni ini, Ouvinte ouvinte) {
        this.cliente = cliente;
        this.ini = ini;
        this.ouvinte = ouvinte;
    }

    void parar() {
        ativo = false;
    }

    @Override
    public void run() {
        while (ativo) {
            long inicio = System.currentTimeMillis();
            try {
                byte[] bloco = cliente.lerCanais(0, ini.tamanhoBloco);
                Map<Grandeza, Double> valores = new EnumMap<>(Grandeza.class);
                for (Grandeza g : Grandeza.values()) {
                    DefinicaoCanal canal = ini.canal(g.canalIni);
                    // Canal ausente neste firmware: NaN -> o mostrador exibe "--".
                    valores.put(g, canal == null ? Double.NaN : canal.ler(bloco) + g.soma);
                }
                ouvinte.aoLer(valores);
            } catch (IOException erro) {
                if (ativo) {
                    ouvinte.aoFalhar(erro.getMessage());
                }
                return;
            }
            long espera = INTERVALO_MS - (System.currentTimeMillis() - inicio);
            if (espera > 0) {
                try {
                    Thread.sleep(espera);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
