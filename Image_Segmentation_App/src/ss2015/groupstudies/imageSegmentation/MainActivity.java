package ss2015.groupstudies.imageSegmentation;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

public class MainActivity extends Activity implements OnTouchListener, OnClickListener
{
	private static final String TAG = "ImageSegemenation";
	
	private static int deviceWidth, deviceHeight;
	private static ImageView iv, ivTransparent;
	private static Button loadImage, nextBtn, prevBtn, fg, bg, erase;

	private static Canvas mCanvas;
	private static Paint mPaint, clearPaint;

	private static int rotation = 0;
	private static float scale;

	private static final int SELECT_PHOTO = 100;

	private static final int REQUEST_IMAGE_CAPTURE = 200;
	
	private static final int originalImageViewWidth = 432;
	
	private static final int originalImageViewHeight = 525;
	
	private static boolean loadImageSuccess = false;

	private static RelativeLayout.LayoutParams params;

	private static Bitmap overlayBitmap, masterImg, bitmapRGBA, mergedBitmap, foregroundBitmap;

	private static Uri selectedImage;
	private static InputStream imageStream;

	private static float beginCoordsX, beginCoordsY, endCoordsX, endCoordsY;

	boolean isForeground = false;
	boolean isRectSet = false;
	boolean isoverlayCreated = false;
	boolean chooseAlgorithm = false;
	boolean isErase = false;
	boolean isForegroundbutton = false;

	private int counterfg  = 0;
	private int counterbg = 0;
	
	Mat matRGBA, mat3C, mask;
	
	Point fgPoints, bgPoints, erasePoints;
	List<Point> fgPointsList, bgPointsList, erasePointsList;

	private static final CharSequence[] items = {" WaterShed "," GrabCut "};
	
	public MainActivity() 
	{
		Log.i(TAG, "Instantiated new " + this.getClass());
	}

	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this){
		@Override
		public void onManagerConnected(int status){
			switch (status){
			case LoaderCallbackInterface.SUCCESS: {
				Log.i(TAG, "OpenCV loaded successfully");

			}
			break;
			default: {
				super.onManagerConnected(status);
			}
			break;
			}
		}
	};

	// called when activity is first created
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		Log.i(TAG, "called onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		iv = (ImageView) MainActivity.this.findViewById(R.id.imageView);

		ivTransparent = (ImageView) MainActivity.this.findViewById(R.id.imageViewTransparent);
		ivTransparent.setVisibility(View.GONE);

		loadImage = (Button) MainActivity.this.findViewById(R.id.loadImage);
		
		nextBtn = (Button) MainActivity.this.findViewById(R.id.nextBtn);
		
		fg = (Button) MainActivity.this.findViewById(R.id.foreGround);
		fg.setVisibility(View.GONE);
		
		bg = (Button) MainActivity.this.findViewById(R.id.backGround);
		bg.setVisibility(View.GONE);
		
		prevBtn = (Button) MainActivity.this.findViewById(R.id.prevBtn);;
		prevBtn.setVisibility(View.GONE);
		
		erase = (Button) MainActivity.this.findViewById(R.id.erase);
		erase.setVisibility(View.GONE);



		loadImage.setOnClickListener(this);
		nextBtn.setOnClickListener(this);
		fg.setOnClickListener(this);

		
		bg.setOnClickListener(this);
		prevBtn.setOnClickListener(this);
		erase.setOnClickListener(this);

		deviceWidth = getWindowManager().getDefaultDisplay().getWidth();
		deviceHeight = getWindowManager().getDefaultDisplay().getHeight();
		Log.w(TAG, "Device Screen Resolution: " +deviceWidth + "*" + deviceHeight);

		mPaint = new Paint(Paint.DITHER_FLAG);
		mPaint.setAntiAlias(true);
		mPaint.setDither(true);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeJoin(Paint.Join.ROUND);
		mPaint.setStrokeCap(Paint.Cap.ROUND);
		
		fgPointsList = new ArrayList<Point>();
		bgPointsList = new ArrayList<Point>();
	
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.

		// Settings: Choose Segmentation Algorithm

		switch (item.getItemId()) 
		{
		case R.id.item2:
			Toast.makeText(this, "Help", Toast.LENGTH_SHORT).show();
			help();
			return true;

		case R.id.item3:
			Toast.makeText(this, "Select Stroke Width", Toast.LENGTH_SHORT).show();
			return true;

		case R.id.submenu3:
			if (item.isChecked())
				item.setChecked(false);
			else
				item.setChecked(true);
			Toast.makeText(this, "Thin", Toast.LENGTH_SHORT).show();
			mPaint.setStrokeWidth(6f);
			return true;
		case R.id.submenu4:
			if (item.isChecked())
				item.setChecked(false);
			else
				item.setChecked(true);
			Toast.makeText(this, "Medium", Toast.LENGTH_SHORT).show();
			mPaint.setStrokeWidth(10f);
			return true;
		case R.id.submenu5:
			if (item.isChecked())
				item.setChecked(false);
			else
				item.setChecked(true);
			Toast.makeText(this, "Large", Toast.LENGTH_SHORT).show();
			mPaint.setStrokeWidth(14f);
			return true;
		case R.id.item4:
			Toast.makeText(this, "Reset", Toast.LENGTH_SHORT).show();
			overlayBitmap.eraseColor(0);
			counterfg = 0;
			counterbg = 0;
			if(chooseAlgorithm)
			{
				resetGrabCut();
			}

			iv.setImageBitmap(masterImg);
			return true;
		
		case R.id.item6:
			Toast.makeText(this, "Save", Toast.LENGTH_SHORT).show();
			save();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	private void resetGrabCut()
	{
		fgPointsList.clear();
		bgPointsList.clear();
		if(isErase)
			erasePointsList.clear();
		mask.setTo(new Scalar(Imgproc.GC_PR_FGD));
	}
	
	@Override
	public void onClick(View v) 
	{		
		switch (v.getId()) 
		{
		case R.id.loadImage:
			
			params = new RelativeLayout.LayoutParams(originalImageViewWidth, originalImageViewHeight);
			iv.setLayoutParams(params);
			ivTransparent.setLayoutParams(params);
			selectImage();
			
			break;

		case R.id.nextBtn:
			if(loadImageSuccess)
			{
				loadImage.setVisibility(View.GONE);
				nextBtn.setVisibility(View.GONE);

				fg.setVisibility(View.VISIBLE);
				bg.setVisibility(View.VISIBLE);
				prevBtn.setVisibility(View.VISIBLE);
				erase.setVisibility(View.VISIBLE);
				
				AlertDialog.Builder alertDialogBuilderAlgo  = new AlertDialog.Builder(MainActivity.this);
				alertDialogBuilderAlgo.setTitle("Select segmentation Algorithm!");
				alertDialogBuilderAlgo.setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() 
				{
					public void onClick(DialogInterface dialog, int item) 
					{
						switch(item)
						{
						case 0:
							
							chooseAlgorithm = false;
							break;
						case 1:
							
							chooseAlgorithm = true;
							createGrabCutmask();	
							break;
						}
						dialog.dismiss();   
					}
				});
				AlertDialog alertDialogAlgo = alertDialogBuilderAlgo.create();
				alertDialogAlgo.show();	
			}
			else
			{
				AlertDialog.Builder alertDialogBuilder  = new AlertDialog.Builder(MainActivity.this);
				alertDialogBuilder.setTitle("Warning!");
				alertDialogBuilder.setMessage("Please select an image")
				.setCancelable(false)
				.setNeutralButton("OK", new DialogInterface.OnClickListener() 
				{
					public void onClick(DialogInterface dialog,int id) 
					{
						dialog.dismiss();
					}
				});
				// create alert dialog
				AlertDialog alertDialog = alertDialogBuilder.create();

				// show it
				alertDialog.show();

			}
			break;

		case R.id.prevBtn:
			counterfg = 0;
			counterbg = 0;
			loadImageSuccess = false;
			loadImage.setVisibility(View.VISIBLE);
			nextBtn.setVisibility(View.VISIBLE);

			fg.setVisibility(View.GONE);
			bg.setVisibility(View.GONE);
			prevBtn.setVisibility(View.GONE);
			erase.setVisibility(View.GONE);

			iv.invalidate();

			RelativeLayout.LayoutParams originalParams = (RelativeLayout.LayoutParams) iv.getLayoutParams();
			originalParams.width = originalImageViewWidth;
			originalParams.height = originalImageViewHeight;

			iv.setLayoutParams(originalParams);

			Log.d(TAG, "Layout Param: Width*Height: " + originalParams.width + "*" + originalParams.height);

			Drawable myDrawable = getResources().getDrawable(R.drawable.ic_launcher);
			masterImg = ((BitmapDrawable) myDrawable).getBitmap();
			iv.setImageBitmap(masterImg);

			ivTransparent.invalidate();
			ivTransparent.setVisibility(View.GONE);
			
			if(chooseAlgorithm)
				resetGrabCut();
			break;

		case R.id.foreGround:
			isForegroundbutton = true;		
			setOverlay();
			counterfg++;
			
			break;

		case R.id.backGround:
			
			if(isoverlayCreated)
			{
				isForeground = false;

				bg.setBackgroundColor(getResources().getColor(R.color.blue));
				mPaint.setColor(Color.BLUE);
				counterbg++;
				//counter++;
				ivTransparent.invalidate();
			}
			/*else
			{
				AlertDialog.Builder alertDialogBuilder  = new AlertDialog.Builder(MainActivity.this);
				alertDialogBuilder.setTitle("Warning!");
				alertDialogBuilder.setMessage("Please press FG button")
				.setCancelable(false)
				.setNeutralButton("OK", new DialogInterface.OnClickListener() 
				{
					public void onClick(DialogInterface dialog,int id) 
					{
						dialog.dismiss();
					}
				});
				// create alert dialog
				AlertDialog alertDialog = alertDialogBuilder.create();

				// show it
				alertDialog.show();	
			}*/
			break;
			
		case R.id.erase:
			
			if(isoverlayCreated)
			{
				erase.setBackgroundColor(getResources().getColor(R.color.blue));
				if(chooseAlgorithm)
					erasePointsList = new ArrayList<Point>();
				clearPaint = new Paint(); 
				clearPaint.setColor(Color.TRANSPARENT);
				clearPaint.setStrokeWidth(mPaint.getStrokeWidth());
				clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
				clearPaint.setStyle(Paint.Style.STROKE);
				clearPaint.setStrokeWidth(20f);
				clearPaint.setStrokeJoin(Paint.Join.ROUND);
				clearPaint.setStrokeCap(Paint.Cap.ROUND);
				ivTransparent.setOnTouchListener(this);
				isErase = true;
			}
			
			break;

		default:
			break;
		}
	}
	
	@Override
	public boolean onTouch(View v, MotionEvent event) 
	{
		int action = event.getAction();
		switch (action) 
		{
			case MotionEvent.ACTION_DOWN:
				if(!isErase)
				{
					if(isForeground)
					{
						beginCoordsX = event.getX();
				    	beginCoordsY = event.getY();
				    	
				    	fgPoints = new Point();
				    	fgPoints.x= beginCoordsX;
				    	fgPoints.y= beginCoordsY;
				    	
				    	fgPointsList.add(fgPoints);
				    	
				    	fg.setBackgroundColor(getResources().getColor(R.color.blue));
				    	bg.setBackgroundColor(getResources().getColor(R.color.lightgray));
				    	Log.i(TAG, "Start Drawing foreground");
					}
					else
					{
						beginCoordsX = event.getX();
				    	beginCoordsY = event.getY();
				    	
				    	bgPoints = new Point();
				    	bgPoints.x= beginCoordsX;
				    	bgPoints.y= beginCoordsY;
				    	
				    	bgPointsList.add(bgPoints);
				    	
				    	bg.setBackgroundColor(getResources().getColor(R.color.blue));
				    	fg.setBackgroundColor(getResources().getColor(R.color.lightgray));
				    	Log.i(TAG, "Start Drawing background");
					}
				}
				else
				{
					erase.setBackgroundColor(getResources().getColor(R.color.blue));
					fg.setBackgroundColor(getResources().getColor(R.color.lightgray));
					bg.setBackgroundColor(getResources().getColor(R.color.lightgray));
					beginCoordsX = event.getX();
					beginCoordsY = event.getY();
					
					erasePoints = new Point();
					erasePoints.x= beginCoordsX;
					erasePoints.y= beginCoordsY;
					if(chooseAlgorithm)
						erasePointsList.add(erasePoints);
					
					Log.d(TAG, "Start erase..");
				}
				ivTransparent.invalidate();
				break;
			
			case MotionEvent.ACTION_MOVE:
				if(!isErase)
				{
					if(isForeground)
					{
						endCoordsX = event.getX();
				    	endCoordsY = event.getY();
				    	mCanvas.drawLine(beginCoordsX, beginCoordsY, endCoordsX, endCoordsY, mPaint);

				    	fgPoints = new Point();
				    	fgPoints.x= endCoordsX;
				    	fgPoints.y= endCoordsY;
				    	fgPointsList.add(fgPoints);
				    	
				    	beginCoordsX = endCoordsX;
				    	beginCoordsY = endCoordsY;
				    	fg.setBackgroundColor(getResources().getColor(R.color.blue));
				    	Log.d(TAG, "Foreground drawing in progress..");
					}
					else
					{
						endCoordsX = event.getX();
				    	endCoordsY = event.getY();
				    	mCanvas.drawLine(beginCoordsX, beginCoordsY, endCoordsX, endCoordsY, mPaint);

				    	bgPoints = new Point();
				    	bgPoints.x= endCoordsX;
				    	bgPoints.y= endCoordsY;
				    	bgPointsList.add(bgPoints);
				    	
				    	beginCoordsX = endCoordsX;
				    	beginCoordsY = endCoordsY;
				    	bg.setBackgroundColor(getResources().getColor(R.color.blue));
				    	Log.d(TAG, "Background drawing in progress..");
					}		
				}
				else
				{
					erase.setBackgroundColor(getResources().getColor(R.color.blue));
					endCoordsX = event.getX();
					endCoordsY = event.getY();
					mCanvas.drawLine(beginCoordsX, beginCoordsY, endCoordsX, endCoordsY, clearPaint);
					
					erasePoints = new Point();
					erasePoints.x= endCoordsX;
					erasePoints.y= endCoordsY;
					if(chooseAlgorithm)
					{
						erasePointsList.add(erasePoints);
					}
					
			    	
			    	beginCoordsX = endCoordsX;
			    	beginCoordsY = endCoordsY;
			    	
			    	Log.d(TAG, "Erase drawing in progress..");
				}
				
				ivTransparent.invalidate();
				break;
			
			case MotionEvent.ACTION_UP:
				if(!isErase)
				{
					if(isForeground)
					{
						fg.setBackgroundColor(getResources().getColor(R.color.lightgray));
						
						Log.d(TAG, "Foreground end Drawing..");
						if(!chooseAlgorithm)
						{
							if(counterfg>=1 && counterbg>=1){
								watershed();
							}
						}
						else
						{
							if(counterfg>=1 && counterbg>=1){
								grabcut();
							}
						}
					}
						
					else
					{
						bg.setBackgroundColor(getResources().getColor(R.color.lightgray));
						Log.d(TAG, "Foreground end Drawing..");
						if(counterfg!=0 && counterbg!=0)
						{
							if(!chooseAlgorithm)
							{
								watershed(); 
							}
							else
							{
								grabcut();
							}
						}
					}
				}
				else
				{
					erase.setBackgroundColor(getResources().getColor(R.color.lightgray));
					isErase = false;
					isForeground = false;
					
					if(chooseAlgorithm)
					{
						for(int i=0; i<erasePointsList.size();i++)
						{
							Point p = new Point(erasePointsList.get(i).x, (int)erasePointsList.get(i).y);
							Core.circle( mask, p, 1, new Scalar(Imgproc.GC_PR_FGD ), 1);
							
						}
					}
							
					Log.d(TAG, "Erase drawing end..");
				}
			
				ivTransparent.invalidate();
				break;
		case MotionEvent.ACTION_CANCEL:
			break;
		default:
			break;
		}
		if(isForegroundbutton)
			return true;
		else
			return false;
	}

	
	private void setOverlay()
	{
		if(isForegroundbutton)
		{
			ivTransparent.setVisibility(View.VISIBLE);

			isForeground = true;

			fg.setBackgroundColor(getResources().getColor(R.color.blue));

			mPaint.setAntiAlias(true);
			mPaint.setDither(true);

			mPaint = new Paint();
			mPaint.setColor(Color.RED);
			mPaint.setStrokeWidth(6f);
			mPaint.setStyle(Paint.Style.STROKE);
			mPaint.setStrokeJoin(Paint.Join.ROUND);
			mPaint.setStrokeCap(Paint.Cap.ROUND);

			ivTransparent.setLayoutParams(params);

			Log.d(TAG, "Overlay imageView dimensions: width*height: "+ ivTransparent.getWidth() + "*" + ivTransparent.getHeight());
			Log.i(TAG, "Foreground Overlay created.");

			Log.i(TAG, "Overlay Bitmap: width*height: " + overlayBitmap.getWidth() + "*" + overlayBitmap.getHeight());

			mCanvas = new Canvas(overlayBitmap);

			Log.i(TAG, "Canvas: width*height: " + mCanvas.getWidth() + "*" + mCanvas.getHeight());

			Matrix matrixFG = new Matrix();

			mCanvas.drawBitmap(overlayBitmap, matrixFG, mPaint);

			ivTransparent.invalidate();

			ivTransparent.setImageBitmap(overlayBitmap);
			loadImageSuccess = true;
			
			ivTransparent.setOnTouchListener(this);
			isoverlayCreated = true;
		}
	}
	
	private void selectImage()
	{
		final CharSequence[] items = { "Take Photo", "Choose from Gallery", "Cancel" };
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		builder.setTitle("Add Photo!");
		builder.setItems(items, new DialogInterface.OnClickListener() 
		{
			@Override
			public void onClick(DialogInterface dialog, int item) 
			{
				if (items[item].equals("Take Photo")) 
				{
					Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
					startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
				}

				else if (items[item].equals("Choose from Gallery")) 
				{
					Intent photoPickerIntent = new Intent(Intent.ACTION_PICK,
							android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
					photoPickerIntent.setType("image/*");
					startActivityForResult(photoPickerIntent, SELECT_PHOTO);
				} 

				else if (items[item].equals("Cancel")) 
				{
					dialog.dismiss();
				}
			}
		});
		builder.show();
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode,Intent imageReturnedIntent) 
	{

		super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
		isRectSet = false;
		switch (requestCode) 
		{
		case SELECT_PHOTO:
			if (resultCode == RESULT_OK) {
				selectedImage = imageReturnedIntent.getData();
				try 
				{
					Log.i(TAG, "Load Image from Gallery");
					imageStream = getContentResolver().openInputStream(selectedImage);

					Bitmap LoadImg = BitmapFactory.decodeStream(imageStream);

					String[] filePathColumn = {MediaStore.Images.Media.DATA};

					Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
					cursor.moveToFirst();

					int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
					String filePath = cursor.getString(columnIndex);
					cursor.close();

					rotation = getPhotoOrientation(MainActivity.this, selectedImage, filePath);

					Log.i(TAG, "Image dimensions: Width*Height: " + LoadImg.getWidth() + "*" + LoadImg.getHeight());

					Log.i(TAG, "ImageView dimensions: Width*Height: " + iv.getWidth() + "*" + iv.getHeight());

					masterImg = scaleImage(LoadImg, LoadImg.getWidth(), LoadImg.getHeight());

					iv.setImageBitmap(masterImg);

					loadImageSuccess = true;
					
					//Bitmap for our segmentation algorithm
					bitmapRGBA = Bitmap.createScaledBitmap(masterImg, masterImg.getWidth(), masterImg.getHeight(), true);
					
					//Bitmap for overlay strokes
					overlayBitmap = Bitmap.createBitmap(masterImg.getWidth(), masterImg.getHeight(), Bitmap.Config.ARGB_8888);

				} 
				catch (FileNotFoundException e) 
				{
					e.printStackTrace();
				}
			}
		case REQUEST_IMAGE_CAPTURE: 
		{
			if (requestCode == REQUEST_IMAGE_CAPTURE) 
			{
				Log.i(TAG, "Take a Picture and display");

				//Check if your application folder exists in the external storage, if not create it:
				File imageStorageFolder = new File(Environment.getExternalStorageDirectory()+
						File.separator+"Segmentation");
				if (!imageStorageFolder.exists())
				{
					imageStorageFolder.mkdirs();
					Log.d(TAG , "Folder created at: "+imageStorageFolder.toString());
				}

				//Check if data in not null and extract the Bitmap:
				if (imageReturnedIntent != null)
				{
					String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
					String filename = "image" + timeStamp;
					String fileNameExtension = ".png";
					File sdCard = Environment.getExternalStorageDirectory();
					String imageFolder = File.separator+"Segmentation"+File.separator;
					File destinationFile = new File(sdCard, imageFolder + filename + fileNameExtension);
					Log.d(TAG, "the destination for image file is: " + destinationFile );

					if (imageReturnedIntent.getExtras() != null)
					{
						Bitmap capturedImage = (Bitmap)imageReturnedIntent.getExtras().get("data");
						
						masterImg = Bitmap.createBitmap(capturedImage);
						try
						{
							FileOutputStream out = new FileOutputStream(destinationFile);
							capturedImage.compress(Bitmap.CompressFormat.PNG, 100, out);
							out.flush();
							out.close();
						} 
						catch (Exception e) 
						{
							Log.e(TAG, "ERROR:" + e.toString());
						}
						
						masterImg = scaleImage(masterImg, masterImg.getWidth(), masterImg.getHeight());
						
						iv.setImageBitmap(masterImg);
						loadImageSuccess = true;
						
						//Bitmap for our segmentation algorithm
						bitmapRGBA = Bitmap.createScaledBitmap(masterImg, masterImg.getWidth(), masterImg.getHeight(), true);
						
						//Bitmap for overlay strokes
						overlayBitmap = Bitmap.createBitmap(masterImg.getWidth(), masterImg.getHeight(), Bitmap.Config.ARGB_8888);
					}
				}
			}
		}
	}
}

	private int getPhotoOrientation(Context context, Uri imageUri, String imagePath)
	{
		int rotate = 0;
		try 
		{
			context.getContentResolver().notifyChange(imageUri, null);
			File imageFile = new File(imagePath);

			ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
			int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

			switch (orientation) 
			{
			case ExifInterface.ORIENTATION_ROTATE_270:
				rotate = 270;
				break;
			case ExifInterface.ORIENTATION_ROTATE_180:
				rotate = 180;
				break;
			case ExifInterface.ORIENTATION_ROTATE_90:
				rotate = 90;
				break;
			}

			Log.i(TAG, "Exif orientation: " + orientation);
			Log.i(TAG, "Rotate value: " + rotate);
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		return rotate;
	}
	private Bitmap scaleImage(Bitmap image, int originalImageWidth, int originalImageHeight)
	{		
		scale = 0;
		
		int imageViewWidth = originalImageViewWidth;
		int imageViewHeight = originalImageViewHeight;
		int imageWidth = 0;
		int imageHeight = 0;
		if(rotation == 0)
		{
			imageWidth = image.getWidth();
			imageHeight = image.getHeight();
			
			Log.i(TAG, "Image dimension: Width*Height: " +imageWidth + "*" + imageHeight);
		}
		else
		{
			imageWidth = image.getHeight();
			imageHeight = image.getWidth();
			
			Log.i(TAG, " Rotated Image dimension: Width*Height: " +imageWidth + "*" + imageHeight);
		}
		

		float xScale = ((float) imageViewWidth) / imageWidth;
		float yScale = ((float) imageViewHeight) / imageHeight;
		scale = (xScale <= yScale) ? xScale : yScale;

		Log.d(TAG, "Xscale: " +xScale);
		Log.d(TAG, "Yscale: " +yScale);
		Log.d(TAG, "scale: " + scale);

		// Create a matrix for the scaling and add the scaling data
		Matrix matrix = new Matrix();
		matrix.postScale(scale, scale);
		matrix.postRotate(rotation);

		Bitmap scaledBitmap = Bitmap.createBitmap(image, 0, 0, originalImageWidth, originalImageHeight, matrix, true);
		imageWidth = scaledBitmap.getWidth();
		imageHeight = scaledBitmap.getHeight();

		Log.i(TAG, "scaled Image dimensions: " + imageWidth + "*" + imageHeight);

		params = new RelativeLayout.LayoutParams(imageWidth, imageHeight);
		
		if(imageHeight > imageWidth)
		{
			params.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
			params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
		}
		if(imageHeight < imageWidth)
		{
			params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
		}
		
		iv.setLayoutParams(params);
		ivTransparent.setLayoutParams(params);
	
		Log.i(TAG, "Params: width*height: " + params.width + "*" + params.height);
		
		return scaledBitmap;
	}
	
	private void createGrabCutmask()
	{
		//4 channel Mat
				matRGBA = new Mat();
				Utils.bitmapToMat(bitmapRGBA, matRGBA);
				//3 channel Mat
				mat3C = new Mat();
				Imgproc.cvtColor(matRGBA, mat3C, Imgproc.COLOR_RGBA2RGB);
				//Mask created
				mask = new Mat(mat3C.size(), CvType.CV_8UC1);
				mask.setTo(new Scalar(Imgproc.GC_PR_FGD));
	}
	private void grabcut()
	{
//		//4 channel Mat
//		matRGBA = new Mat();
//		Utils.bitmapToMat(bitmapRGBA, matRGBA);
//		//3 channel Mat
//		mat3C = new Mat();
//		Imgproc.cvtColor(matRGBA, mat3C, Imgproc.COLOR_RGBA2RGB);
//		//Mask created
//		mask = new Mat(mat3C.size(), CvType.CV_8UC1);
//		mask.setTo(new Scalar(Imgproc.GC_PR_FGD));
		
		//Setting up foreground information
		for(int i=0; i<fgPointsList.size();i++)
		{			
			Point p = new Point(fgPointsList.get(i).x, (int)fgPointsList.get(i).y);
			
			Core.circle( mask, p, 1, new Scalar(Imgproc.GC_FGD ), 1 );
			
		}
	
		//Setting background information to mask
		for(int i=0; i<bgPointsList.size();i++)
		{
			
			Point p = new Point(bgPointsList.get(i).x, (int)bgPointsList.get(i).y);
			
			Core.circle( mask, p, 1, new Scalar(Imgproc.GC_BGD ), 1 );
		}

		Log.d(TAG, "Mask FG & BG set: "+ mask);
		Log.w(TAG, "Mask dump: "+ mask.dump());
		

		Mat bgdModel = new Mat();
		bgdModel.setTo(new Scalar(255, 255, 255));
		Mat fgdModel = new Mat();
		fgdModel.setTo(new Scalar(255, 255, 255));
		Rect rect = new Rect();
		
		Mat maskClone = new Mat();
		maskClone = mask.clone();
		//GrabCut begins
		Log.i(TAG,"GrabCut initialized");
		Imgproc.grabCut(mat3C, maskClone, rect, bgdModel, fgdModel, 1, Imgproc.GC_INIT_WITH_MASK);
		Log.i(TAG, "GrabCut finished");
		
		Log.d(TAG,"Mask after grabcut" + mask + mask.dump());
	
		
		/*
		for(int i=0; i<fgPointsList.size();i++)
		{			
			Point p = new Point(fgPointsList.get(i).x, (int)fgPointsList.get(i).y);
			
			Core.circle( maskClone, p, 1, new Scalar(Imgproc.GC_PR_FGD), 1 );
			
		}*/
		Mat source = new Mat(1, 1, CvType.CV_8U, new Scalar(3.0));

		Core.compare(maskClone, source, maskClone, Core.CMP_EQ);
		
		Mat fgMat = new Mat(matRGBA.size(), CvType.CV_8UC3, new Scalar(255, 255, 255));
		matRGBA.copyTo(fgMat, maskClone); //All the non zero elements of the mask needs to be copied.

		Log.d(TAG, "foreground: " + fgMat);
		
		Mat grayMat = new Mat();
		Imgproc.cvtColor(mat3C, grayMat, Imgproc.COLOR_RGB2GRAY);
		
		grayMat.convertTo(grayMat, CvType.CV_8UC1);
	
		Mat grayBGMat = new Mat();
		Core.add(mask, grayMat, grayBGMat);
	
		Log.d(TAG, "Gray BG Mat: " + grayBGMat);
		
		//convert to Bitmap
		Log.d(TAG, "Converting to Bitmap");
		
		Bitmap grayBGBitmap = Bitmap.createBitmap(bitmapRGBA.getWidth(), bitmapRGBA.getHeight(), Bitmap.Config.ARGB_8888);
		
		Utils.matToBitmap(grayBGMat, grayBGBitmap, true);
		
		foregroundBitmap = Bitmap.createBitmap(bitmapRGBA.getWidth(), bitmapRGBA.getHeight(), Bitmap.Config.ARGB_8888);
		
		Utils.matToBitmap(fgMat, foregroundBitmap, true);
		
		mergedBitmap = Bitmap.createBitmap(bitmapRGBA.getWidth(), bitmapRGBA.getHeight(), Bitmap.Config.ARGB_8888);
		
		Canvas finalcanvas = new Canvas(mergedBitmap);
		
		finalcanvas.drawBitmap(grayBGBitmap, new Matrix(), null);
		finalcanvas.drawBitmap(foregroundBitmap, 0,  0, null);

		iv.setImageBitmap(mergedBitmap);
		
		fgPointsList.clear();
		bgPointsList.clear();
		
		fgdModel.release();
		bgdModel.release();
		source.release();
		fgMat.release();
		grayMat.release();
		grayBGMat.release();
		maskClone.release();
	}
	
	private void watershed() 
	{
		Mat matRGBA = new Mat();
		Utils.bitmapToMat(bitmapRGBA, matRGBA);

		Mat mask = new Mat();
		//mask.setTo(new Scalar(125));
		Utils.bitmapToMat(overlayBitmap, mask);
		
		Mat mat3C = new Mat();
		Imgproc.cvtColor(matRGBA, mat3C, Imgproc.COLOR_RGBA2RGB);
		
		//Log.w(TAG, "mask input" + mask.dump());
		
		Imgproc.cvtColor(mask, mask, Imgproc.COLOR_RGB2GRAY);
		mask.convertTo(mask, CvType.CV_32S);
		
		//WateShed starts
		 
		Imgproc.watershed(mat3C, mask);
		Log.i(TAG, "out of watershed");
		
		Log.w(TAG, "mask output" + mask.dump());
		
		mask.convertTo(mask, CvType.CV_8U);
		
		Mat source = new Mat(1, 1, CvType.CV_8U, new Scalar(29));
		
		Core.compare(mask, source, mask, Core.CMP_GT);
		
		Log.w(TAG, "mask output compare" + mask.dump());
		
		Mat foreground = new Mat(matRGBA.size(), CvType.CV_8UC3, new Scalar(255, 255, 255));
		
		matRGBA.copyTo(foreground, mask);
		
		Log.w(TAG, "foreground output" + foreground.dump());
		
		Mat element = new Mat();
		//Imgproc.erode(foreground, foreground, element);
		
		Mat grayMat = new Mat();
		Imgproc.cvtColor(mat3C, grayMat, Imgproc.COLOR_RGB2GRAY);
		
		grayMat.convertTo(grayMat, CvType.CV_8UC1);
		
		Mat grayBGMat = new Mat();
		Core.add(mask, grayMat, grayBGMat);
		
		//Imgproc.dilate(grayBGMat, grayBGMat, element);
		
		Log.d(TAG, "Dst: " + grayBGMat);
		
		//convert to Bitmap
		Log.d(TAG, "Convert to Bitmap");

		Bitmap grayBGBitmap = Bitmap.createBitmap(bitmapRGBA.getWidth(), bitmapRGBA.getHeight(), Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(grayBGMat, grayBGBitmap);
		foregroundBitmap = Bitmap.createBitmap(bitmapRGBA.getWidth(), bitmapRGBA.getHeight(), Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(foreground, foregroundBitmap);
		mergedBitmap = Bitmap.createBitmap(bitmapRGBA.getWidth(), bitmapRGBA.getHeight(), Bitmap.Config.ARGB_8888);
		
		Canvas finalcanvas = new Canvas(mergedBitmap);
		finalcanvas.drawBitmap(grayBGBitmap, new Matrix(), null);
		finalcanvas.drawBitmap(foregroundBitmap, 0,  0, null);
		
		//ivTransparent.setVisibility(View.GONE);

		iv.invalidate();
		iv.setImageBitmap(mergedBitmap);

		matRGBA.release();
		mask.release();
		element.release();
		grayBGMat.release();
		grayMat.release();
		
	}


	protected void save()
	{		
		//Check if your application folder exists in the external storage, if not create it:
		File imageStorageFolder = new File(Environment.getExternalStorageDirectory()+
				File.separator+"Segmentation");
		if (!imageStorageFolder.exists())
		{
			imageStorageFolder.mkdirs();
			Log.d(TAG , "Folder created at: "+imageStorageFolder.toString());
		}
		
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String filename = "image_mask" + timeStamp;
		String fileNameExtension = ".png";
		File sdCard = Environment.getExternalStorageDirectory();
		String imageFolder = File.separator+"Segmentation"+File.separator;
		File destinationFile = new File(sdCard, imageFolder + filename + fileNameExtension);
		Log.d(TAG, "the destination for image file is: " + destinationFile );
		
		String filenamefg = "fg_mask" + timeStamp;
		File destinationFilefg = new File(sdCard, imageFolder + filenamefg + fileNameExtension);
	
		try 
		{
			FileOutputStream out = new FileOutputStream(destinationFile);
			FileOutputStream outFG = new FileOutputStream(destinationFilefg);
			
			BufferedOutputStream bos = new BufferedOutputStream(out);
			BufferedOutputStream bosfg = new BufferedOutputStream(outFG);
			if(mergedBitmap != null)
			{
				mergedBitmap.compress(CompressFormat.PNG, 100, bos);
			}
			if(foregroundBitmap!= null)
			{

				foregroundBitmap.compress(CompressFormat.PNG, 100, bosfg);	
			}
			
			bos.flush();
			bos.close();
			bosfg.flush();
			bosfg.close();
			Log.i(TAG,"Inside save try");

		} 
		catch (FileNotFoundException e)
		{
			Log.w("TAG", "Error saving image file: " + e.getMessage());
		}
		catch (IOException e) 
		{
			Log.w("TAG", "Error saving image file: " + e.getMessage());
		}		
	}


	@Override
	public void onPause() {
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallback);
	}


	@Override
	public void onDestroy()
	{
		super.onDestroy();
		
		//matRGBA.release();
		//mask.release();
		//mat3C.release();
	}

	private final void help()
	{

		AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
		alertDialog.setTitle("Help");
		alertDialog.setMessage("\n This program demonstrates GrabCut and Watershed Image segmentation\n" 
				
				+"\n\nKindly click on Select Photo Button\n" 
				
				+ "\nChoose how do you wish to get the image\n" +
				
				"\n\nClick Next:\n" +
				
				"\n\nSelect the desired Algorithm for Image Segmentation\n" +
				
				"\n\nMark the desired Foreground and Background regions\n" +
				
				"\n\nReady to go, your image is segmented as per your wish: \n" +
				
				"\n\n HOT KEYS : \n" +
				
				"\n\n\bReset - Restores the original image\n" +
				"\n\n\bErase - Erase the marked foreground and background\n" +
				"\n\n\bStroke Width - Selects the desired stroke width \n" +
				
				"\n\n\bSave - Saves the segmented image to the storage directory\n");
		alertDialog.show();
		iv.setVisibility(View.VISIBLE);
	}
}

