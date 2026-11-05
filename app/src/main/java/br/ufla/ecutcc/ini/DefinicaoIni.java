package br.ufla.ecutcc.ini;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O que o app precisa de um .ini do Speeduino: a assinatura do firmware, o tamanho
 * do bloco de dados ao vivo e a posição de cada canal pelo nome.
 *
 * É assim que o app se adapta a outra versão do firmware sem mudar o código: se a
 * equipe do Speeduino mudar um canal de lugar, o .ini daquela versão diz onde ele está.
 */
public final class DefinicaoIni {

    private static final Pattern SCALAR = Pattern.compile(
            "^\\s*(\\w+)\\s*=\\s*scalar\\s*,\\s*([US]\\d{2})\\s*,\\s*(\\d+)\\s*,\\s*\"[^\"]*\"\\s*,"
                    + "\\s*([-+0-9.eE]+)\\s*,\\s*([-+0-9.eE]+)");
    private static final Pattern CHAVE_VALOR = Pattern.compile("^\\s*(\\w+)\\s*=\\s*(.+?)\\s*$");

    public final String assinatura;
    public final int tamanhoBloco;
    private final Map<String, DefinicaoCanal> canais;

    private DefinicaoIni(String assinatura, int tamanhoBloco, Map<String, DefinicaoCanal> canais) {
        this.assinatura = assinatura;
        this.tamanhoBloco = tamanhoBloco;
        this.canais = canais;
    }

    /** null se o canal não existir neste .ini. */
    public DefinicaoCanal canal(String nome) {
        return canais.get(nome);
    }

    public static DefinicaoIni ler(InputStream entrada) throws IOException {
        // Os .ini do Speeduino estão em ISO-8859-1 (por causa do "°").
        BufferedReader leitor = new BufferedReader(
                new InputStreamReader(entrada, Charset.forName("ISO-8859-1")));
        String assinatura = null;
        int tamanhoBloco = -1;
        Map<String, DefinicaoCanal> canais = new HashMap<>();
        String secao = "";
        String linha;
        while ((linha = leitor.readLine()) != null) {
            int comentario = linha.indexOf(';');
            if (comentario >= 0) {
                linha = linha.substring(0, comentario);
            }
            String limpa = linha.trim();
            if (limpa.isEmpty() || limpa.startsWith("#")) {
                continue; // diretivas #if/#else: vale a primeira definição encontrada
            }
            if (limpa.startsWith("[") && limpa.endsWith("]")) {
                secao = limpa.substring(1, limpa.length() - 1);
                continue;
            }
            if (secao.equals("MegaTune") && assinatura == null) {
                Matcher m = CHAVE_VALOR.matcher(linha);
                if (m.matches() && m.group(1).equals("signature")) {
                    assinatura = m.group(2).replace("\"", "").trim();
                }
            } else if (secao.equals("OutputChannels")) {
                Matcher m = SCALAR.matcher(linha);
                if (m.find()) {
                    String nome = m.group(1);
                    if (!canais.containsKey(nome)) {
                        canais.put(nome, new DefinicaoCanal(nome, m.group(2),
                                Integer.parseInt(m.group(3)),
                                Double.parseDouble(m.group(4)), Double.parseDouble(m.group(5))));
                    }
                    continue;
                }
                Matcher kv = CHAVE_VALOR.matcher(linha);
                if (kv.matches() && kv.group(1).equals("ochBlockSize") && tamanhoBloco < 0) {
                    tamanhoBloco = Integer.parseInt(kv.group(2).trim());
                }
            }
        }
        if (tamanhoBloco <= 0 || canais.isEmpty()) {
            throw new IOException("Arquivo .ini sem a seção [OutputChannels] esperada");
        }
        return new DefinicaoIni(assinatura, tamanhoBloco, canais);
    }
}
