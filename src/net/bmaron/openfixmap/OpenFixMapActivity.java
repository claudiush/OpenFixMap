package net.bmaron.openfixmap;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import net.bmaron.openfixmap.R;
import net.bmaron.openfixmap.ErrorParsers.ErrorPlateform;
import net.bmaron.openfixmap.ErrorParsers.KeepRight;
import net.bmaron.openfixmap.ErrorParsers.OpenStreetBugs;

import org.osmdroid.util.BoundingBoxE6;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.tileprovider.tilesource.CloudmadeTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.util.CloudmadeUtil;
import org.osmdroid.views.MapController;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedIconOverlay.OnItemGestureListener;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.MyLocationOverlay;

		
public class OpenFixMapActivity extends Activity {


    protected MapView mapView;
	
    private MapController mapController;
    private MyLocationOverlay mMyLocationOverlay;
    private ScaleBarOverlay mScaleBarOverlay;  
    
    static final int DIALOG_ERROR_ID = 0;
    private ItemizedIconOverlay<OverlayErrorItem> pointOverlay; 
    private SharedPreferences sharedPrefs; 
	private Handler mHandler;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(); 
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(R.layout.main);

        mapView = (MapView) findViewById(R.id.mapview);

        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);

        loadMapSource(2);
        


        /* Set position of last open or near Home :) */

        SharedPreferences settings = getSharedPreferences("last_position", 0);
        int lat = settings.getInt("last_position_lat",50838599);
        int lon = settings.getInt("last_position_lon",4406551);
        int zoom = settings.getInt("last_position_zoom",16);
        
        mapController = this.mapView.getController();
        mapController.setZoom(zoom);
        GeoPoint p = new GeoPoint(lat, lon);
        
        mapController.setCenter(p);

        this.mMyLocationOverlay = new MyLocationOverlay(this, this.mapView);                          
        this.mapView.getOverlays().add(mMyLocationOverlay);
        this.mMyLocationOverlay.enableMyLocation();
        //this.mMyLocationOverlay.enableCompass();

        mMyLocationOverlay.runOnFirstFix(new Runnable() {
            public void run() {
            	if(sharedPrefs.getBoolean("go_to_first", false)) {
            		goToCurrenLocation();            		
            	}
            	
            }
        });
        
        this.mScaleBarOverlay = new ScaleBarOverlay(this);                          
        this.mapView.getOverlays().add(mScaleBarOverlay);
        

    	class myItemGestureListener<T extends OverlayErrorItem> implements OnItemGestureListener<T> {
		    
    		private T it;
		    @Override
		    public boolean onItemSingleTapUp(int index, T item) {
		    	it = item;
		        mHandler.post(new Runnable() {
				    public void run() { 
				    	 ProblemDialog dialog = new ProblemDialog(OpenFixMapActivity.this,it.getError());
				    	 dialog.show();
				    	}
				}); 
		        return false;
		    }
		
		    @Override
		    public boolean onItemLongPress(int index, T item) { return false;}
    	}

        OnItemGestureListener<OverlayErrorItem> pOnItemGestureListener = new myItemGestureListener<OverlayErrorItem>();
       
        pointOverlay = new ItemizedIconOverlay<OverlayErrorItem>(this, new ArrayList<OverlayErrorItem>(), pOnItemGestureListener);
        this.mapView.getOverlays().add(pointOverlay);

    	if(sharedPrefs.getBoolean("fetch_on_launch", false)) {
    		Handler handler=new Handler();
    		Runnable r=new Runnable() {
    		    public void run() {
    		    	loadDataSource();                       
    		    }
    		};
    		handler.postDelayed(r, 2000);    //Wait to Be Painted		           		
    	}
    }
    
    @Override
    protected void onStop(){
       super.onStop();

      // We need an Editor object to make preference changes.
      // All objects are from android.context.Context
      SharedPreferences settings = getSharedPreferences("last_position", 0);
      SharedPreferences.Editor editor = settings.edit();
      BoundingBoxE6 bb = mapView.getBoundingBox();
      editor.putInt("last_position_lat", bb.getCenter().getLatitudeE6());
      editor.putInt("last_position_lon",  bb.getCenter().getLongitudeE6());
      editor.putInt("last_position_zoom", mapView.getZoomLevel());

      // Commit the edits!
      editor.commit();
    }

    
    protected  List<ErrorItem> fetchDatas()
    {
        String [] checkers = MultiSelectListPreference.parseStoredValue(sharedPrefs.getString("checkers", "KeepRights"));
        boolean show_closed = sharedPrefs.getBoolean("show_closed", false);
        List<ErrorItem> items = new ArrayList<ErrorItem>();
        BoundingBoxE6 bb = mapView.getBoundingBox();

    	
    	int display_level = Integer.parseInt(sharedPrefs.getString("display_level", "1"));
    	ErrorPlateform bugPlateform;
        for(int i = 0; i < checkers.length; i++) {
        	
        	if(checkers[i].equals("OpenStreetBugs")) {
        		bugPlateform = new OpenStreetBugs(bb, display_level, show_closed);
            	
        	} else// if(checkers[i].equals("KeepRight")) 
        	{
        		bugPlateform = new KeepRight(bb, display_level, show_closed);
        	}
        	
        	bugPlateform.load();
        	items.addAll(bugPlateform.getItems());
        }
        
    	Toast toast = Toast.makeText(this, items.size()+" items downloaded", Toast.LENGTH_SHORT);
    	toast.show();
        return items;
    }
    
    protected void loadDataSource()
    {
    	Resources res = getResources();
    	pointOverlay.removeAllItems();
        List<ErrorItem> itemList = fetchDatas();
        
        for(int i=0; i < itemList.size(); i++) {
        	ErrorItem item = itemList.get(i);
        	OverlayErrorItem oItem = new OverlayErrorItem(item);
        	oItem.setMarker( res.getDrawable(R.drawable.caution));
            pointOverlay.addItem(oItem);
        }
        mapView.invalidate();

    }
    
    protected void loadMapSource(int source)
    {
    	switch(source) {
    		case 1:
    			CloudmadeTileSource map_source_cloud = (CloudmadeTileSource)TileSourceFactory.CLOUDMADESTANDARDTILES;
    			map_source_cloud.setStyle(999);
    		    CloudmadeUtil.retrieveCloudmadeKey(this);
    	        mapView.setTileSource(map_source_cloud);
    			break;
    		case 2:
    			CloudmadeTileSource map_source_noname = (CloudmadeTileSource)TileSourceFactory.CLOUDMADESTANDARDTILES;
    			map_source_noname.setStyle(3);
    		    CloudmadeUtil.retrieveCloudmadeKey(this);
    	        mapView.setTileSource(map_source_noname);		
    			break;
    		case 3:
    			mapView.setTileSource(TileSourceFactory.MAPQUESTOSM);
    			break;
    	        /*XYTileSource name = new XYTileSource("name", null, 0, 16, 256, ".png", "http://tilesserver.com/");
        		mapView.setTileSource(name); */
    		default:
    			break;
    	}

    }
    protected void goToCurrenLocation()
    {
    	mHandler.post(new Runnable() {
		    public void run() { 
		    	if(mMyLocationOverlay.getMyLocation() != null) {
		            // Make sure setZoom is before animateTo ==> may be a Go to sahara animateTo bug 
            		mapController.setZoom(17);
    		        mapController.animateTo(mMyLocationOverlay.getMyLocation());
            	}
		    }
    	});       
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.map_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
    	switch (item.getItemId()) {
        	case R.id.gotolocation:
        		goToCurrenLocation();
        		return true;
        		
        	case R.id.refresh:
        		loadDataSource();
        		return true;
        	case R.id.preferences: 
        		startActivityForResult(new Intent(this, Preferences.class), 1 /* CODE_RETOUR*/);
        		return true;
        	default:
        		return super.onOptionsItemSelected(item);
        }
    }
}
