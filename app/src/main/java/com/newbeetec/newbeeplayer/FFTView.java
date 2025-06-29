package com.newbeetec.newbeeplayer;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.Locale;

public class FFTView extends View {
    private static final int SAMPLE_SIZE = 1024;
    private static final float MIN_DB = -40f;

    private final float[] magnitudes = new float[SAMPLE_SIZE / 2];
    private final Paint paint = new Paint();
    private final Path path = new Path();
    private final Paint gridPaint = new Paint();
    private final Paint textPaint = new Paint();

    private final float[] freqLabels = {20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000};
    private final float[] logPositions = new float[freqLabels.length];

    public FFTView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setColor(getResources().getColor(R.color.spectrum_line));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setAntiAlias(true);

        gridPaint.setColor(getResources().getColor(R.color.grid_line));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(12f);
        textPaint.setAntiAlias(true);

        // 预计算对数位置
        float maxLog = (float) Math.log10(20000);
        float minLog = (float) Math.log10(20);
        for (int i = 0; i < freqLabels.length; i++) {
            float logVal = (float) Math.log10(freqLabels[i]);
            logPositions[i] = (logVal - minLog) / (maxLog - minLog);
        }
    }

    public void updateFFT(byte[] fftData) {
        // 转换为实部/虚部
        float[] fft = new float[fftData.length / 2];
        for (int i = 0; i < fftData.length; i += 2) {
            float real = Math.abs(fftData[i]);
            float img = Math.abs(fftData[i + 1]);
            magnitudes[i / 2] = (float) Math.sqrt(real * real + img * img);
        }

        // 转换为分贝值
        for (int i = 0; i < magnitudes.length; i++) {
            magnitudes[i] = magnitudeToDb(magnitudes[i]);
        }

        postInvalidate();
    }

    private float magnitudeToDb(float magnitude) {
        return (float) (magnitude==0?100:20 * Math.log10(magnitude/128)); // 避免log(0)
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (magnitudes == null || magnitudes.length == 0) return;

        int width = getWidth();
        int height = getHeight();

        drawGrid(canvas, width, height);
        drawFFT(canvas, width, height);
    }

    private void drawGrid(Canvas canvas, int width, int height) {
        // 绘制频率刻度
        for (int i = 0; i < freqLabels.length; i++) {
            float x = logPositions[i] * width;
            canvas.drawLine(x, 0, x, height, gridPaint);

            String label = formatFreqLabel(freqLabels[i]);
            canvas.drawText(label, x - 10, height - 5, textPaint);
        }

        // 绘制分贝刻度
        for (int db = (int) MIN_DB; db <= 0; db += 10) {
            float y = height * (1 - (db - MIN_DB) / -MIN_DB);
            canvas.drawText(db + "dB", 5, y + 5, textPaint);
            canvas.drawLine(0, y, width, y, gridPaint);
        }
    }

    private String formatFreqLabel(float freq) {
        if (freq >= 1000) {
            return String.format(Locale.US, "%.0fk", freq / 1000);
        }
        return String.format(Locale.US, "%.0f", freq);
    }

    private void drawFFT(Canvas canvas, int width, int height) {
        path.reset();
        float maxLog = (float) Math.log10(20000);
        float minLog = (float) Math.log10(20);

        float range = -MIN_DB;

        for (int i = 1; i < magnitudes.length; i++) {
            // 对数坐标转换
            float freq = i * 20000f / magnitudes.length;
            float logFreq = (float) Math.log10(freq);
            float x = width * (logFreq - minLog) / (maxLog - minLog);
            if(magnitudes[i]==100) {
                if (i == 1) {
                    path.moveTo(50, height);
                } else {
                    path.lineTo(x, height);
                }
            }else{
                float magnitude = Math.max(MIN_DB, Math.min(0, magnitudes[i]));
                float normalized = (magnitude - MIN_DB) / range;
                float y = height * (1 - normalized);

                if (i == 1) {
                    path.moveTo(50, (int)y);
                } else {
                    path.lineTo(x, (int)y);
                }
            }

        }

        canvas.drawPath(path, paint);
    }
}