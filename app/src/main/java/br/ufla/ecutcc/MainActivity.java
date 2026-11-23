package br.ufla.ecutcc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
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
 * Tela única: escolher o módulo (pareado ou achado pela busca do app, que também
 * faz o pareamento), conectar, ver as 8 grandezas e trocar o
 * layout (relógios ou números grandes). A UI é montada em código para não depender
 * de bibliotecas (minSdk 21).
 */
@SuppressLint("MissingPermission") // a permissão é conferida em temPermissao()
public final class MainActivity extends Activity implements LeituraContinua.Ouvinte {

    private static final int PEDIDO_PERMISSAO = 1;
    private static final int PEDIDO_ARQUIVO_INI = 2;
    private static final String PREF_LAYOUT_RELOGIO = "layout_relogio";
    private static final String PREF_ULTIMO_MODULO = "ultimo_modulo"; // endereço do último módulo conectado

    private final Handler tela = new Handler(Looper.getMainLooper());
    private final Map<Grandeza, Mostrador> mostradores = new EnumMap<>(Grandeza.class);
    private final List<BluetoothDevice> dispositivos = new ArrayList<>(); // pareados + achados na busca
    private final List<String> nomesDispositivos = new ArrayList<>();
    private ArrayAdapter<String> adaptadorLista;
    private BluetoothDevice aguardandoPareamento; // conectar assim que o pareamento terminar
    private boolean receptorRegistrado;

    private Spinner listaDispositivos;
    private Button botaoConectar;
    private Button botaoBuscar;
    private TextView status;
    private LinearLayout painel;
    private boolean layoutRelogio;

    private ConexaoBluetooth conexao;
    private ClienteSpeeduino cliente;
    private LeituraContinua leitura;
    private String versaoPendente; // versão cujo .ini o usuário está escolhendo

    // Reconexão automática quando a conexão cai (distância, interferência).
    private static final int MAX_TENTATIVAS = 10;
    private static final long ESPERA_ENTRE_TENTATIVAS_MS = 3000;
    private BluetoothDevice ultimoAlvo;      // módulo conectado por último (null = usuário desconectou)
    private volatile boolean reconectando;
    private volatile boolean reconexaoCancelada; // Cancelar tocado com uma tentativa em andamento
    private int tentativa;
    private AlertDialog avisoReconexao;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        layoutRelogio = preferencias().getBoolean(PREF_LAYOUT_RELOGIO, true);
        montarTela();
        registrarReceptor();
        if (temPermissao()) {
            carregarPareados();
        } else {
            requestPermissions(permissoesNecessarias(), PEDIDO_PERMISSAO);
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
        botaoBuscar = new Button(this);
        botaoBuscar.setText("Buscar");
        botaoBuscar.setOnClickListener(v -> buscarDispositivos());
        Button botaoLayout = new Button(this);
        botaoLayout.setText("Layout");
        botaoLayout.setOnClickListener(v -> trocarLayout());
        barra.addView(listaDispositivos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        barra.addView(botaoBuscar);
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

    /** Android 12+: conectar e buscar. Até o 11: a busca exige localização. */
    private String[] permissoesNecessarias() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    /** Permissão para conectar (sem ela o app não funciona). */
    private boolean temPermissao() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    /** Permissão para buscar módulos não pareados. */
    private boolean podeBuscar() {
        for (String p : permissoesNecessarias()) {
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int pedido, String[] permissoes, int[] resultados) {
        if (pedido == PEDIDO_PERMISSAO) {
            if (temPermissao()) {
                carregarPareados();
                if (podeBuscar() && dispositivos.isEmpty()) {
                    buscarDispositivos();
                }
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
        dispositivos.clear();
        nomesDispositivos.clear();
        for (BluetoothDevice d : adaptador.getBondedDevices()) {
            adicionarDispositivo(d);
        }
        adaptadorLista = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, nomesDispositivos);
        listaDispositivos.setAdapter(adaptadorLista);
        selecionarUltimoModulo();
        if (dispositivos.isEmpty()) {
            mostrarStatus("Nenhum módulo pareado. Toque em Buscar com a ECU ligada.");
        }
    }

    /** Já deixa escolhido o módulo usado da última vez (em vez do primeiro pareado da lista). */
    private void selecionarUltimoModulo() {
        String ultimo = preferencias().getString(PREF_ULTIMO_MODULO, null);
        for (int i = 0; ultimo != null && i < dispositivos.size(); i++) {
            if (dispositivos.get(i).getAddress().equals(ultimo)) {
                listaDispositivos.setSelection(i);
                return;
            }
        }
    }

    private void adicionarDispositivo(BluetoothDevice d) {
        for (BluetoothDevice existente : dispositivos) {
            if (existente.getAddress().equals(d.getAddress())) {
                return;
            }
        }
        dispositivos.add(d);
        String nome = d.getName() != null ? d.getName() : "(sem nome)";
        String marca = d.getBondState() == BluetoothDevice.BOND_BONDED ? "" : " [novo]";
        nomesDispositivos.add(nome + marca + " (" + d.getAddress() + ")");
        if (adaptadorLista != null) {
            adaptadorLista.notifyDataSetChanged();
        }
    }

    /** Depois de parear, o módulo deixa de ser "[novo]" na lista. */
    private void tirarMarcaNovo(BluetoothDevice d) {
        for (int i = 0; i < dispositivos.size(); i++) {
            if (dispositivos.get(i).getAddress().equals(d.getAddress())) {
                nomesDispositivos.set(i, nomesDispositivos.get(i).replace(" [novo]", ""));
                if (adaptadorLista != null) {
                    adaptadorLista.notifyDataSetChanged();
                }
                return;
            }
        }
    }

    // ---------------------------------------------------------------- busca e pareamento pelo app

    private void buscarDispositivos() {
        if (!podeBuscar()) {
            requestPermissions(permissoesNecessarias(), PEDIDO_PERMISSAO);
            return;
        }
        BluetoothAdapter adaptador = BluetoothAdapter.getDefaultAdapter();
        if (adaptador == null || !adaptador.isEnabled()) {
            mostrarStatus("Ligue o Bluetooth do celular.");
            return;
        }
        if (adaptador.isDiscovering()) {
            adaptador.cancelDiscovery();
        }
        if (adaptador.startDiscovery()) {
            botaoBuscar.setEnabled(false);
            mostrarStatus("Buscando módulos Bluetooth (~12 s)... deixe a ECU ligada.");
        } else {
            mostrarStatus("Não foi possível iniciar a busca (no Android 11 ou menos, ligue a localização).");
        }
    }

    private void registrarReceptor() {
        IntentFilter filtro = new IntentFilter();
        filtro.addAction(BluetoothDevice.ACTION_FOUND);
        filtro.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filtro.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receptor, filtro, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receptor, filtro);
        }
        receptorRegistrado = true;
    }

    private final BroadcastReceiver receptor = new BroadcastReceiver() {
        @Override
        public void onReceive(Context contexto, Intent intent) {
            String acao = intent.getAction();
            BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_FOUND.equals(acao) && d != null) {
                adicionarDispositivo(d);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(acao)) {
                botaoBuscar.setEnabled(true);
                mostrarStatus("Busca terminada: " + dispositivos.size()
                        + " módulo(s) na lista. Escolha o da ECU e toque em Conectar.");
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(acao) && d != null
                    && aguardandoPareamento != null
                    && d.getAddress().equals(aguardandoPareamento.getAddress())) {
                int estado = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                if (estado == BluetoothDevice.BOND_BONDED) {
                    tirarMarcaNovo(d);
                    BluetoothDevice alvo = aguardandoPareamento;
                    aguardandoPareamento = null;
                    reconexaoCancelada = false;
                    mostrarStatus("Pareado. Conectando a " + alvo.getName() + "...");
                    new Thread(() -> conectar(alvo), "conexao").start();
                } else if (estado == BluetoothDevice.BOND_NONE) {
                    aguardandoPareamento = null;
                    botaoConectar.setEnabled(true);
                    mostrarStatus("Pareamento cancelado ou PIN errado (o padrão do HC-06 é 1234).");
                }
            }
        }
    };

    // ---------------------------------------------------------------- conexão

    private void alternarConexao() {
        if (conexao != null) {
            ultimoAlvo = null; // desconexão pedida pelo usuário: não reconectar
            desconectar("Desconectado");
            return;
        }
        int i = listaDispositivos.getSelectedItemPosition();
        if (!temPermissao() || i < 0 || i >= dispositivos.size()) {
            mostrarStatus("Escolha um módulo (ou toque em Buscar).");
            return;
        }
        BluetoothDevice alvo = dispositivos.get(i);
        botaoConectar.setEnabled(false);
        if (alvo.getBondState() != BluetoothDevice.BOND_BONDED) {
            // Pareia pelo próprio app: o Android mostra a caixa do PIN (HC-06: 1234).
            aguardandoPareamento = alvo;
            if (!alvo.createBond()) {
                aguardandoPareamento = null;
                botaoConectar.setEnabled(true);
                mostrarStatus("Não foi possível iniciar o pareamento.");
            } else {
                mostrarStatus("Pareando com " + alvo.getName() + ": digite o PIN (padrão 1234).");
            }
            return;
        }
        reconexaoCancelada = false;
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
            if (reconexaoCancelada) {
                fecharConexao(); // a tentativa terminou depois do Cancelar
                return;
            }
            ultimoAlvo = alvo;
            preferencias().edit().putString(PREF_ULTIMO_MODULO, alvo.getAddress()).apply();
            iniciarLeitura(ini, assinatura, produto);
            if (reconectando) {
                reconectando = false;
                tela.post(this::fecharAvisoReconexao);
            }
        } catch (IOException erro) {
            if (reconexaoCancelada) {
                fecharConexao();
            } else if (reconectando) {
                fecharConexao();
                tela.postDelayed(this::proximaTentativa, ESPERA_ENTRE_TENTATIVAS_MS);
            } else {
                desconectar("Falha na conexão: " + erro.getMessage());
                if (alvo.getBondState() == BluetoothDevice.BOND_BONDED) {
                    tela.post(() -> oferecerNovoPareamento(alvo));
                }
            }
        }
    }

    /**
     * O HC-06 guarda a chave de pareamento de poucos aparelhos: se outro aparelho (ex.: o
     * notebook) conectar nele, a chave salva no celular pode deixar de valer e a conexão
     * cai na autenticação. Em vez de mandar o usuário às configurações, o app oferece
     * apagar o pareamento e parear de novo (PIN 1234).
     */
    private void oferecerNovoPareamento(BluetoothDevice alvo) {
        new AlertDialog.Builder(this)
                .setTitle("Não conectou")
                .setMessage("O módulo foi encontrado, mas recusou a conexão. Isso costuma acontecer "
                        + "quando o pareamento salvo ficou velho (por exemplo, se outro aparelho "
                        + "conectou no módulo). Parear de novo agora?")
                .setPositiveButton("Parear de novo", (d, w) -> refazerPareamento(alvo))
                .setNegativeButton("Agora não", null)
                .show();
    }

    private void refazerPareamento(BluetoothDevice alvo) {
        try {
            // removeBond() é oculto na API pública, mas é o mesmo que "Esquecer" nas configurações.
            alvo.getClass().getMethod("removeBond").invoke(alvo);
        } catch (Exception semRemover) {
            mostrarStatus("Não consegui apagar o pareamento: use Esquecer nas configurações do Bluetooth.");
            return;
        }
        mostrarStatus("Pareamento apagado. Pareando de novo: digite o PIN (padrão 1234).");
        botaoConectar.setEnabled(false);
        // Dá um tempo para o Android terminar de apagar antes de parear de novo.
        // (aguardandoPareamento só depois, senão o aviso de "desparear" é lido como falha.)
        tela.postDelayed(() -> {
            aguardandoPareamento = alvo;
            if (!alvo.createBond()) {
                aguardandoPareamento = null;
                botaoConectar.setEnabled(true);
                mostrarStatus("Não foi possível iniciar o pareamento. Tente Buscar e Conectar.");
            }
        }, 1500);
    }

    // ---------------------------------------------------------------- reconexão automática

    /** Conexão caiu: tenta de novo a cada 3 s, até 10 vezes, com aviso e botão Cancelar. */
    private void iniciarReconexao() {
        fecharConexao();
        reconexaoCancelada = false;
        reconectando = true;
        tentativa = 0;
        tela.post(() -> {
            botaoConectar.setEnabled(false);
            for (Mostrador m : mostradores.values()) {
                m.mostrar(Double.NaN);
            }
            avisoReconexao = new AlertDialog.Builder(this)
                    .setTitle("Conexão perdida")
                    .setMessage("Reconectando...")
                    .setCancelable(false)
                    .setNegativeButton("Cancelar", (d, w) -> cancelarReconexao())
                    .show();
            tela.postDelayed(this::proximaTentativa, ESPERA_ENTRE_TENTATIVAS_MS);
        });
    }

    /** Roda na thread da tela. */
    private void proximaTentativa() {
        if (!reconectando || ultimoAlvo == null) {
            return;
        }
        tentativa++;
        if (tentativa > MAX_TENTATIVAS) {
            reconectando = false;
            fecharAvisoReconexao();
            desconectar("Conexão perdida: não reconectou após " + MAX_TENTATIVAS + " tentativas.");
            return;
        }
        String texto = "Reconectando... (tentativa " + tentativa + " de " + MAX_TENTATIVAS + ")";
        if (avisoReconexao != null) {
            avisoReconexao.setMessage(texto);
        }
        mostrarStatus("Conexão perdida. " + texto);
        BluetoothDevice alvo = ultimoAlvo;
        new Thread(() -> conectar(alvo), "reconexao").start();
    }

    private void cancelarReconexao() {
        reconexaoCancelada = true;
        reconectando = false;
        tela.removeCallbacksAndMessages(null);
        fecharAvisoReconexao();
        desconectar("Conexão perdida (reconexão cancelada).");
    }

    private void fecharAvisoReconexao() {
        if (avisoReconexao != null) {
            avisoReconexao.dismiss();
            avisoReconexao = null;
        }
    }

    /** Fecha leitura e socket sem mexer na tela. */
    private void fecharConexao() {
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
        fecharConexao();
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
        if (ultimoAlvo != null && !reconectando) {
            iniciarReconexao();
        } else {
            desconectar("Conexão perdida: " + mensagem);
        }
    }

    @Override
    protected void onDestroy() {
        reconectando = false;
        ultimoAlvo = null;
        tela.removeCallbacksAndMessages(null);
        fecharAvisoReconexao();
        if (receptorRegistrado) {
            unregisterReceiver(receptor);
            receptorRegistrado = false;
        }
        desconectar("Desconectado");
        super.onDestroy();
    }
}
