// ---------------------------------------------------------------------
//
// XPlaneMonitor
//
// Copyright (C) 2017 Wayne Piekarski
// wayne@tinmith.net http://tinmith.net/wayne
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
// ---------------------------------------------------------------------


package net.waynepiekarski.xplanemonitor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import junit.framework.Assert;

public class GraphView extends View {

    private Paint foreground;
    private Paint background;
    private Paint leader;
    private double mMax;
    private int step;
    private int stepNext;
    private int stepPrev;
    private double current[];
    private double prev[];
    private Bitmap bitmap;
    private Canvas canvas;
    private Paint paint[];
    private int palette[] = { Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW };

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        foreground = new Paint();
        foreground.setColor(Color.LTGRAY);
        background = new Paint();
        background.setColor(Color.BLACK);
        leader = new Paint();
        leader.setColor(Color.DKGRAY);
        paint = new Paint[palette.length];
        for (int i = 0; i < palette.length; i++) {
            paint[i] = new Paint();
            paint[i].setColor(palette[i]);
        }
        reset();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        this.setMeasuredDimension(parentWidth, parentHeight);
    }

    private void clearBitmap() {
        if ((bitmap != null) || (canvas != null)) {
            canvas.drawColor(background.getColor());
            canvas.drawRect(0, 0, canvas.getWidth() - 1, 0, foreground);
            canvas.drawRect(canvas.getWidth() - 1, 0, canvas.getWidth() - 1, canvas.getHeight() - 1, foreground);
            canvas.drawRect(canvas.getWidth() - 1, canvas.getHeight() - 1, 0, canvas.getHeight() - 1, foreground);
            canvas.drawRect(0, canvas.getHeight() - 1, 0, 0, foreground);
        }
    }

    @Override
    protected void onDraw(Canvas liveCanvas) {
        super.onDraw(canvas);

        if ((bitmap == null) || (bitmap.getWidth() != canvas.getWidth()) || (bitmap.getHeight() != canvas.getHeight())) {
            bitmap = Bitmap.createBitmap(liveCanvas.getWidth(), liveCanvas.getHeight(), Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
            clearBitmap();
        }

        // Clear out pixels on the current column before we draw here
        canvas.drawLine(step, 0, step, canvas.getHeight(), background);
        canvas.drawLine(stepNext, 0, stepNext, canvas.getHeight(), leader);

        // Plot the latest data at the current column
        if (current != null) {
            for (int i = 0; i < current.length; i++) {
                int x1 = stepPrev;
                int y1 = (int)(canvas.getHeight()/2.0 + prev[i]/mMax*canvas.getHeight() / 2.0);
                int x2 = step;
                int y2 = (int)(canvas.getHeight()/2.0 + current[i]/mMax*canvas.getHeight() / 2.0);

                // Only draw if there is no wrap-around
                if (x2 > x1)
                    canvas.drawLine(x1, y1, x2, y2, paint[i]);
            }
        }

        liveCanvas.drawBitmap(bitmap, 0, 0, foreground);

        step += 1;
        if (step > canvas.getWidth())
            step = 0;
        stepNext += 1;
        if (stepNext > canvas.getWidth())
            stepNext = 0;
        stepPrev += 1;
        if (stepPrev > canvas.getWidth())
            stepPrev = 0;

        // Save the current values as previous values for the next run
        double temp[] = prev;
        prev = current;
        current = temp;
    }

    public void setSize(int length) {
        if (length > palette.length)
            length = palette.length;
        if ((current == null) || (length != current.length)) {
            current = new double[length];
            prev = new double[length];
        }
    }

    static public int min(int a, int b) {
        if (a < b)
            return a;
        else
            return b;
    }

    public void setValues(float[] in) {
        Assert.assertEquals("Mismatch between incoming length " + current.length + " with existing " + in.length, min(in.length,palette.length), current.length);
        for (int i = 0; i < min(in.length, palette.length); i++)
            current[i] = in[i];
        invalidate();
    }

    public void set1Value(double in) {
        Assert.assertEquals("Mismatch between incoming length " + current.length + " with existing 1", 1, current.length);
        current[0] = in;
        invalidate();
    }

    public void resetMaximum(double in) {
        reset();
        mMax = in;
        invalidate();
    }

    public void reset() {
        current = null;
        prev = null;
        step = 0;
        stepNext = step + 1;
        stepPrev = step - 1;
        mMax = 1.0;
        clearBitmap();
    }
}
