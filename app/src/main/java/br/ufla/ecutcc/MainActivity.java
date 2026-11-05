package br.ufla.ecutcc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import br.ufla.ecutcc.bluetooth.ConexaoBluetooth;
import br.ufla.ecutcc.ini.DefinicaoIni;
import br.ufla.ecutcc.ini.RepositorioIni;
import br.ufla.ecutcc.modelo.Grandeza;
import br.ufla.ecutcc.protocolo.ClienteSpeeduino;
import br.ufla.ecutcc.ui.Mostrador;
import br.ufla.ecutcc.ui.MostradorNumero;
import br.ufla.ecutcc.ui.MostradorRelogio;

/**
 * Tela única: escolher o módulo pareado, conectar, ver as 8 grandezas e trocar o
 * layout (relógios ou números grandes). A UI é montada em código para não depender
 * de bibliotecas (minSdk 21).
 */
@SuppressLint("MissingPermission") // a permissão é conferida em temPermissao()
public final class MainActivity extends Activity implements LeituraContinua.Ouvinte {

    private static final int PEDIDO_PERMISSAO = 1;
    private static final int PEDIDO_ARQUIVO_INI = 2;
    private static final String PREF_LAYOUT_RELOGIO = "layout_relogio";

    private final Handler tela = new Handler(Looper.getMainLooper());
    private final Map<Grandeza, Mostrador> mostradores = new EnumMap<>(Grandeza.class);
    private final List<BluetoothDevice> pareados = new ArrayList<>();

    private Spinner listaDispositivos;
    private Button botaoConectar;
    private TextView status;
    private LinearLayout painel;
    private boolean layoutRelogio;

    private ConexaoBluetooth conexao;
    private ClienteSpeeduino cliente;
    private LeituraContinua leitura;
    private String versaoPendente; // versão cujo .ini o usuário está escolhendo

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        layoutRelogio = preferencias().getBoolean(PREF_LAYOUT_RELOGIO, true);
        montarTela();
        if (temPermissao()) {
            carregarPareados();
        } else {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PEDIDO_PERMISSAO);
        }
    }

    // ---------------------------------------------------------------- tela

    private void montarTela() {
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(Color.BLACK);

        LinearLayout barra = new LinearLayout(this);
        listaDispositivos = new Spinner(this);
        botaoConectar = new Button(this);
        botaoConectar.setText("Conectar");
        botaoConectar.setOnClickListener(v -> alternarConexao());
        Button botaoLayout = new Button(this);
        botaoLayout.setText("Layout");
        botaoLayout.setOnClickListener(v -> trocarLayout());
        barra.addView(listaDispositivos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        barra.addView(botaoConectar);
        barra.addView(botaoLayout);
        raiz.addView(barra);

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setPadding(16, 4, 16, 4);
        status.setText("Desconectado");
        raiz.addView(status);

        painel = new LinearLayout(this);
        painel.setOrientation(LinearLayout.VERTICAL);
        raiz.addView(painel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(raiz);
        montarMostradores();
    }

    /** 8 mostradores em 4 linhas de 2. */
    private void montarMostradores() {
        painel.removeAllViews();
        mostradores.clear();
        Grandeza[] todas = Grandeza.values();
        for (int i = 0; i < todas.length; i += 2) {
            LinearLayout linha = new LinearLayout(this);
            for (int j = i; j < Math.min(i + 2, todas.length); j++) {
                Mostrador m = layoutRelogio
                        ? new MostradorRelogio(this, todas[j])
                        : new MostradorNumero(this, todas[j]);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                p.setMargins(4, 4, 4, 4);
                linha.addView(m, p);
                mostradores.put(todas[j], m);
            }
            painel.addView(linha, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }
    }

    private void trocarLayout() {
        layoutRelogio = !layoutRelogio;
        preferencias().edit().putBoolean(PREF_LAYOUT_RELOGIO, layoutRelogio).apply();
        montarMostradores();
    }

    private SharedPreferences preferencias() {
        return getSharedPreferences("ecutcc", MODE_PRIVATE);
    }

    private void mostrarStatus(String texto) {
        tela.post(() -> status.setText(texto));
    }

    // ---------------------------------------------------------------- permissão e pareados

    private boolean temPermissao() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int pedido, String[] permissoes, int[] resultados) {
        if (pedido == PEDIDO_PERMISSAO) {
            if (temPermissao()) {
                carregarPareados();
            } else {
                mostrarStatus("Sem permissão de Bluetooth: o app não consegue conectar.");
            }
        }
    }

    private void carregarPareados() {
        BluetoothAdapter adaptador = BluetoothAdapter.getDefaultAdapter();
        if (adaptador == null || !adaptador.isEnabled()) {
            mostrarStatus("Ligue o Bluetooth e pareie o módulo (ex.: ECU_TCC) nas configurações.");
            return;
        }
        pareados.clear();
        List<String> nomes = new ArrayList<>();
        for (BluetoothDevice d : adaptador.getBondedDevices()) {
            pareados.add(d);
            nomes.add(d.getName() + " (" + d.getAddress() + ")");
        }
        listaDispositivos.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, nomes));
    }

    // ---------------------------------------------------------------- conexão

    private void alternarConexao() {
        if (conexao != null) {
            desconectar("Desconectado");
            return;
        }
        int i = listaDispositivos.getSelectedItemPosition();
        if (!temPermissao() || i < 0 || i >= pareados.size()) {
            mostrarStatus("Escolha um módulo pareado.");
            return;
        }
        BluetoothDevice alvo = pareados.get(i);
        botaoConectar.setEnabled(false);
        mostrarStatus("Conectando a " + alvo.getName() + "...");
        new Thread(() -> conectar(alvo), "conexao").start();
    }

    /** Roda fora da thread da tela. */
    private void conectar(BluetoothDevice alvo) {
        try {
            conexao = ConexaoBluetooth.abrir(alvo);
            cliente = new ClienteSpeeduino(conexao.entrada(), conexao.saida());
            String assinatura = cliente.lerAssinatura();
            String produto = cliente.lerProduto();
            String versao = RepositorioIni.versaoDoProduto(produto);
            mostrarStatus("Firmware: " + produto + ". Procurando a definição dos dados...");
            if (versao == null) {
                throw new IOException("Firmware não reconhecido: " + produto);
            }
            DefinicaoIni ini;
            try {
                ini = new RepositorioIni(this).buscar(versao);
            } catch (IOException semIni) {
                versaoPendente = versao;
                tela.post(this::pedirArquivoIni);
                return;
            }
            iniciarLeitura(ini, assinatura, produto);
        } catch (IOException erro) {
            desconectar("Falha na conexão: " + erro.getMessage());
        }
    }

    private void iniciarLeitura(DefinicaoIni ini, String assinatura, String produto) {
        String aviso = (ini.assinatura != null && !ini.assinatura.equals(assinatura))
                ? " (atenção: o .ini é de \"" + ini.assinatura + "\")" : "";
        mostrarStatus("Conectado: " + produto + aviso);
        leitura = new LeituraContinua(cliente, ini, this);
        new Thread(leitura, "leitura").start();
        tela.post(() -> {
            botaoConectar.setText("Desconectar");
            botaoConectar.setEnabled(true);
        });
    }

    /** Último recurso: o usuário escolhe o .ini (o mesmo usado no TunerStudio). */
    private void pedirArquivoIni() {
        mostrarStatus("Não achei a definição do firmware " + versaoPendente
                + " (sem internet?). Escolha o arquivo .ini.");
        Intent escolher = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        escolher.addCategory(Intent.CATEGORY_OPENABLE);
        escolher.setType("*/*");
        startActivityForResult(escolher, PEDIDO_ARQUIVO_INI);
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent dados) {
        super.onActivityResult(pedido, resultado, dados);
        if (pedido != PEDIDO_ARQUIVO_INI) {
            return;
        }
        if (resultado != RESULT_OK || dados == null || dados.getData() == null) {
            desconectar("Sem o arquivo .ini não dá para interpretar os dados.");
            return;
        }
        Uri arquivo = dados.getData();
        new Thread(() -> {
            try (InputStream entrada = getContentResolver().openInputStream(arquivo)) {
                DefinicaoIni ini = new RepositorioIni(this).importar(versaoPendente, entrada);
                iniciarLeitura(ini, cliente.lerAssinatura(), "firmware " + versaoPendente);
            } catch (IOException | NullPointerException erro) {
                desconectar("Arquivo .ini inválido: " + erro.getMessage());
            }
        }, "importar-ini").start();
    }

    private void desconectar(String mensagem) {
        if (leitura != null) {
            leitura.parar();
            leitura = null;
        }
        if (conexao != null) {
            try {
                conexao.close();
            } catch (IOException ignorado) {
                // já estava fechada
            }
            conexao = null;
        }
        mostrarStatus(mensagem);
        tela.post(() -> {
            botaoConectar.setText("Conectar");
            botaoConectar.setEnabled(true);
            for (Mostrador m : mostradores.values()) {
                m.mostrar(Double.NaN);
            }
        });
    }

    // ---------------------------------------------------------------- LeituraContinua.Ouvinte

    @Override
    public void aoLer(Map<Grandeza, Double> valores) {
        tela.post(() -> {
            for (Map.Entry<Grandeza, Double> e : valores.entrySet()) {
                Mostrador m = mostradores.get(e.getKey());
                if (m != null) {
                    m.mostrar(e.getValue());
                }
            }
        });
    }

    @Override
    public void aoFalhar(String mensagem) {
        desconectar("Conexão perdida: " + mensagem);
    }

    @Override
    protected void onDestroy() {
        desconectar("Desconectado");
        super.onDestroy();
    }
}
