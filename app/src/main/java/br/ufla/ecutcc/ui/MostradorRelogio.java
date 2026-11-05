package br.ufla.ecutcc.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import br.ufla.ecutcc.modelo.Grandeza;

/** Relógio de ponteiro: arco de 270°, ponteiro, valor e rótulo. Em alerta, fica vermelho. */
public final class MostradorRelogio extends Mostrador {

    private static final float INICIO = 135f;
    private static final float VARREDURA = 270f;
    private final RectF arco = new RectF();

    public MostradorRelogio(Context contexto, Grandeza grandeza) {
        super(contexto, grandeza);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float raio = Math.min(w, h) / 2f * 0.85f;
        float cx = w / 2f, cy = h / 2f;
        float traco = raio * 0.12f;

        canvas.drawColor(COR_FUNDO);
        arco.set(cx - raio, cy - raio, cx + raio, cy + raio);

        pincel.setStyle(Paint.Style.STROKE);
        pincel.setStrokeWidth(traco);
        pincel.setColor(0xFF444444);
        canvas.drawArc(arco, INICIO, VARREDURA, false, pincel);

        double fracao = fracao();
        pincel.setColor(corAtual());
        canvas.drawArc(arco, INICIO, (float) (VARREDURA * fracao), false, pincel);

        // Ponteiro
        double angulo = Math.toRadians(INICIO + VARREDURA * fracao);
        pincel.setStrokeWidth(traco * 0.35f);
        pincel.setColor(COR_TEXTO);
        canvas.drawLine(cx, cy,
                (float) (cx + Math.cos(angulo) * raio * 0.75),
                (float) (cy + Math.sin(angulo) * raio * 0.75), pincel);

        // Valor e rótulo
        pincel.setStyle(Paint.Style.FILL);
        pincel.setTextAlign(Paint.Align.CENTER);
        pincel.setTextSize(raio * 0.32f);
        pincel.setColor(emAlerta() ? COR_ALERTA : COR_TEXTO);
        canvas.drawText(valorFormatado(), cx, cy + raio * 0.55f, pincel);
        pincel.setTextSize(raio * 0.17f);
        pincel.setColor(COR_TEXTO);
        canvas.drawText(grandeza.rotulo + " " + grandeza.unidade, cx, cy + raio * 0.85f, pincel);
    }

    private double fracao() {
        if (Double.isNaN(valor)) {
            return 0;
        }
        double f = (valor - grandeza.escalaMin) / (grandeza.escalaMax - grandeza.escalaMin);
        return Math.max(0, Math.min(1, f));
    }
}
