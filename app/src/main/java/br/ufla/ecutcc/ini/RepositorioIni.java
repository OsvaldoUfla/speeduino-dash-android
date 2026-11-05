package br.ufla.ecutcc.ini;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encontra o .ini da versão de firmware gravada na ECU, nesta ordem:
 * 1) embutido no app (assets/ini/&lt;versão&gt;.ini);
 * 2) já baixado antes (pasta interna do app);
 * 3) download do repositório oficial do Speeduino no GitHub (e guarda para as próximas vezes);
 * Se nada der certo, a tela pede ao usuário para escolher o arquivo (ver MainActivity).
 */
public final class RepositorioIni {

    private static final String URL_GITHUB =
            "https://raw.githubusercontent.com/speeduino/speeduino/%s/reference/speeduino.ini";
    /** "Speeduino 2025.01.6" -> "202501.6" (formato das tags no GitHub). */
    private static final Pattern VERSAO = Pattern.compile("(\\d{4})\\.(\\d{2})(?:\\.(\\d+))?");

    private final Context contexto;

    public RepositorioIni(Context contexto) {
        this.contexto = contexto.getApplicationContext();
    }

    /** Converte a resposta do comando S em nome de versão; null se não reconhecer. */
    public static String versaoDoProduto(String produto) {
        Matcher m = VERSAO.matcher(produto);
        if (!m.find()) {
            return null;
        }
        String versao = m.group(1) + m.group(2);
        return m.group(3) != null ? versao + "." + m.group(3) : versao;
    }

    /** Lança IOException se não encontrar; a tela então oferece escolher o arquivo. */
    public DefinicaoIni buscar(String versao) throws IOException {
        String arquivo = versao + ".ini";
        try (InputStream embutido = contexto.getAssets().open("ini/" + arquivo)) {
            return DefinicaoIni.ler(embutido);
        } catch (IOException naoEmbutido) {
            // segue para o cache
        }
        File cache = new File(contexto.getFilesDir(), arquivo);
        if (cache.exists()) {
            try (InputStream emCache = new FileInputStream(cache)) {
                return DefinicaoIni.ler(emCache);
            }
        }
        byte[] baixado = baixar(String.format(URL_GITHUB, versao));
        DefinicaoIni definicao = DefinicaoIni.ler(new ByteArrayInputStream(baixado));
        guardar(cache, baixado);
        return definicao;
    }

    /** Usado quando o usuário escolhe o .ini manualmente. */
    public DefinicaoIni importar(String versao, InputStream entrada) throws IOException {
        byte[] dados = lerTudo(entrada);
        DefinicaoIni definicao = DefinicaoIni.ler(new ByteArrayInputStream(dados));
        if (versao != null) {
            guardar(new File(contexto.getFilesDir(), versao + ".ini"), dados);
        }
        return definicao;
    }

    private static byte[] baixar(String endereco) throws IOException {
        HttpURLConnection conexao = (HttpURLConnection) new URL(endereco).openConnection();
        conexao.setConnectTimeout(10000);
        conexao.setReadTimeout(20000);
        try {
            if (conexao.getResponseCode() != 200) {
                throw new IOException("Versão não encontrada no GitHub (HTTP "
                        + conexao.getResponseCode() + ")");
            }
            try (InputStream entrada = conexao.getInputStream()) {
                return lerTudo(entrada);
            }
        } finally {
            conexao.disconnect();
        }
    }

    private static void guardar(File destino, byte[] dados) throws IOException {
        try (OutputStream saida = new FileOutputStream(destino)) {
            saida.write(dados);
        }
    }

    private static byte[] lerTudo(InputStream entrada) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = entrada.read(buffer)) > 0) {
            saida.write(buffer, 0, n);
        }
        return saida.toByteArray();
    }
}
