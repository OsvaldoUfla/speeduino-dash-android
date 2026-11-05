package br.ufla.ecutcc.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.Locale;

import br.ufla.ecutcc.modelo.Grandeza;

/**
 * Base dos dois layouts (relógio e número grande). Guarda a grandeza, o último valor
 * e a regra do alerta: fora da faixa, o mostrador fica vermelho.
 */
public abstract class Mostrador extends View {

    protected static final int COR_NORMAL = Color.rgb(80, 200, 120);
    protected static final int COR_ALERTA = Color.rgb(230, 50, 50);
    protected static final int COR_TEXTO = Color.WHITE;
    protected static final int COR_FUNDO = Color.rgb(30, 30, 30);

    protected final Grandeza grandeza;
    protected double valor = Double.NaN;
    protected final Paint pincel = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected Mostrador(Context contexto, Grandeza grandeza) {
        super(contexto);
        this.grandeza = grandeza;
    }

    public void mostrar(double novoValor) {
        valor = novoValor;
        invalidate();
    }

    protected boolean emAlerta() {
        return !Double.isNaN(valor) && grandeza.foraDaFaixa(valor);
    }

    protected int corAtual() {
        return emAlerta() ? COR_ALERTA : COR_NORMAL;
    }

    protected String valorFormatado() {
        if (Double.isNaN(valor)) {
            return "--";
        }
        if (grandeza == Grandeza.RPM || grandeza == Grandeza.MAP) {
            return String.format(Locale.US, "%.0f", valor);
        }
        return String.format(Locale.US, "%.1f", valor);
    }
}
