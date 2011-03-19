/*
 * Geopaparazzi - Digital field mapping on Android based devices
 * Copyright (C) 2010  HydroloGIS (www.hydrologis.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.hydrologis.geopaparazzi.maps.overlays;

import static eu.hydrologis.geopaparazzi.util.Constants.E6;

import java.io.IOException;
import java.util.List;

import org.osmdroid.ResourceProxy;
import org.osmdroid.util.BoundingBoxE6;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapView.Projection;
import org.osmdroid.views.overlay.Overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.MotionEvent;
import eu.hydrologis.geopaparazzi.database.DaoGpsLog;
import eu.hydrologis.geopaparazzi.gps.GpsManager;
import eu.hydrologis.geopaparazzi.maps.DataManager;
import eu.hydrologis.geopaparazzi.maps.MapItem;
import eu.hydrologis.geopaparazzi.util.ApplicationManager;
import eu.hydrologis.geopaparazzi.util.debug.Logger;

/**
 * Overlay to show gps logs.
 * 
 * @author Andrea Antonello (www.hydrologis.com)
 */
public class LogsOverlay extends Overlay {

    private final Paint mPaint = new Paint();
    private final Path mPath = new Path();

    private Context context;

    private int decimationFactor;

    final private Rect screenRect = new Rect();

    private boolean touchDragging = false;
    private boolean doDraw = true;
    private boolean gpsUpdate = false;

    public LogsOverlay( final Context ctx, final ResourceProxy pResourceProxy ) {
        super(pResourceProxy);
        this.context = ctx;
        ApplicationManager applicationManager = ApplicationManager.getInstance(context);
        decimationFactor = applicationManager.getDecimationFactor();
    }

    public void setDoDraw( boolean doDraw ) {
        this.doDraw = doDraw;
    }

    public void setGpsUpdate( boolean gpsUpdate ) {
        this.gpsUpdate = gpsUpdate;
    }

    protected void draw( final Canvas canvas, final MapView mapsView, final boolean shadow ) {
        if (touchDragging || shadow || !doDraw || mapsView.isAnimating() || !DataManager.getInstance().areLogsVisible())
            return;

        BoundingBoxE6 boundingBox = mapsView.getBoundingBox();
        float y0 = boundingBox.getLatNorthE6() / E6;
        float y1 = boundingBox.getLatSouthE6() / E6;
        float x0 = boundingBox.getLonWestE6() / E6;
        float x1 = boundingBox.getLonEastE6() / E6;

        Projection pj = mapsView.getProjection();

        int screenWidth = canvas.getWidth();
        int screenHeight = canvas.getHeight();
        screenRect.contains(0, 0, screenWidth, screenHeight);
        mapsView.getScreenRect(screenRect);

        try {
            List<MapItem> gpslogs = DaoGpsLog.getGpslogs(context);
            GpsManager gpsManager = GpsManager.getInstance(context);
            long currentLogId = gpsManager.getCurrentRecordedLogId();

            for( MapItem gpslogItem : gpslogs ) {
                Long id = gpslogItem.getId();
                // Logger.d(LOGTAG, "Handling log: " + id);
                if (!gpslogItem.isVisible()) {
                    // Logger.d(LOGTAG, "...which is not visible");
                    continue;
                }
                if (id == currentLogId) {
                    // Logger.d(LOGTAG, "...which is the current");
                    // we always reread the current log to make it proceed
                    DaoGpsLog.getPathInWorldBoundsByIdDecimated(context, y0, y1, x0, x1, mPath, pj, id, decimationFactor);
                } else {
                    // for the other logs we cache depending on a gps update or a touch draw event
                    if (gpsUpdate) {
                        // draw was triggered by gps moving, we get the cached from before
                        // line = linesInWorldBounds.get(id);
                        gpsUpdate = false;
                    } else {
                        // if the draw comes from no gps update, reread the track
                        // linesInWorldBounds.remove(id);
                        DaoGpsLog.getPathInWorldBoundsByIdDecimated(context, y0, y1, x0, x1, mPath, pj, id, decimationFactor);
                        // linesInWorldBounds.put(id, line);
                    }
                }

                mPaint.setAntiAlias(true);
                mPaint.setColor(Color.parseColor(gpslogItem.getColor()));
                mPaint.setStrokeWidth(gpslogItem.getWidth());
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeJoin(Paint.Join.ROUND);
                mPaint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawPath(mPath, mPaint);

            }
        } catch (IOException e) {
            Logger.e(this, e.getLocalizedMessage(), e);
            e.printStackTrace();
        }

    }

    @Override
    public boolean onTouchEvent( MotionEvent event, MapView mapView ) {
        int action = event.getAction();
        switch( action ) {
        case MotionEvent.ACTION_MOVE:
            touchDragging = true;
            break;
        case MotionEvent.ACTION_UP:
            touchDragging = false;
            mapView.invalidate();
            break;
        }
        return super.onTouchEvent(event, mapView);
    }

}
