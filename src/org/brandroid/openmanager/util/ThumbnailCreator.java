/*
    Open Explorer, an open source file explorer & text editor
    Copyright (C) 2011 Brandon Bowles <brandroid64@gmail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.brandroid.openmanager.util;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.ImageView;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.brandroid.openmanager.R;
import org.brandroid.openmanager.data.BookmarkHolder;
import org.brandroid.openmanager.data.OpenFTP;
import org.brandroid.openmanager.data.OpenFile;
import org.brandroid.openmanager.data.OpenMediaStore;
import org.brandroid.openmanager.data.OpenPath;
import org.brandroid.openmanager.data.OpenServer;
import org.brandroid.openmanager.ftp.FTPManager;
import org.brandroid.utils.BitmapUtils;
import org.brandroid.utils.Logger;
import org.brandroid.utils.LruCache;

public class ThumbnailCreator extends Thread {
	private static HashMap<String, Bitmap> mCacheMap = new HashMap<String, Bitmap>();
	private Handler mHandler;
	
	private static Context mContext;
	private boolean mStop = false;
	
	public static boolean useCache = true;
	public static boolean showThumbPreviews = true; 

	public ThumbnailCreator(Context context, Handler handler) {
		mContext = context;
		mHandler = handler;
	}
	
	public static boolean setThumbnail(ImageView mImage, OpenPath file, int mWidth, int mHeight)
	{
		final String mName = file.getName();
		final String ext = mName.substring(mName.lastIndexOf(".") + 1);
		final String sPath2 = mName.toLowerCase();
		final boolean useLarge = mWidth > 72;
		
		final Context mContext = mImage.getContext();
		
		mImage.setImageResource(getDefaultResourceId(file, mWidth, mHeight));
		if(ext.equalsIgnoreCase("jpeg")|| ext.equalsIgnoreCase("png") ||
				  ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("gif") ||
				  ext.equalsIgnoreCase("bmp") ||
				  ext.equalsIgnoreCase("apk") ||
				  ext.equalsIgnoreCase("mp4") || 
				  ext.equalsIgnoreCase("3gp") || 
				  ext.equalsIgnoreCase("avi") ||
				  ext.equalsIgnoreCase("webm")|| 
				  ext.equalsIgnoreCase("m4v"))
		{

			if(showThumbPreviews && !file.requiresThread()) {

				ThumbnailCreator.setContext(mContext);
				Bitmap thumb = ThumbnailCreator.getThumbnailCache(file.getPath(), mWidth, mHeight);
				
				if(thumb == null)
				{	
					ThumbnailTask task = new ThumbnailTask();
					BookmarkHolder mHolder = null;
					if(file.getTag() != null && file.getTag().getClass().equals(BookmarkHolder.class))
					{
						mHolder = ((BookmarkHolder)file.getTag());
						mHolder.setTask(task);
					}
					try {
						task.execute(new ThumbnailStruct(file, mHolder, mWidth, mHeight));
					} catch(RejectedExecutionException rej) {
						Logger.LogError("Couldn't generate thumbnail because Thread pool was full.", rej);
					}
				}
				if(thumb != null)
				{
					BitmapDrawable bd = new BitmapDrawable(thumb);
					bd.setGravity(Gravity.CENTER);
					mImage.setImageDrawable(bd);
				}
			
			}
			
		}
		return false;
	}
	
	public static int getDefaultResourceId(OpenPath file, int mWidth, int mHeight)
	{
		final String mName = file.getName();
		final String ext = mName.substring(mName.lastIndexOf(".") + 1);
		final String sPath2 = mName.toLowerCase();
		final boolean useLarge = mWidth > 72;
		
		if(file.isDirectory()) {
			if(file.requiresThread())
				return (useLarge ? R.drawable.lg_ftp : R.drawable.ftp);
			if(file.getAbsolutePath().equals("/") && mName.equals(""))
				return (R.drawable.drive);
			else if(sPath2.indexOf("download") > -1)
				return (R.drawable.download);
			else if(mName.equals("Photos"))
				return (useLarge ? R.drawable.lg_photo : R.drawable.photo);
			else if(mName.equals("Videos"))
				return (useLarge ? R.drawable.lg_movie : R.drawable.movie);
			else if(mName.equals("Music"))
				return (useLarge ? R.drawable.lg_music : R.drawable.music);
			else if(sPath2.indexOf("ext") > -1 || sPath2.indexOf("sdcard") > -1 || sPath2.indexOf("microsd") > -1)
				return (R.drawable.sdcard);
			else if(sPath2.indexOf("usb") > -1 || sPath2.indexOf("removeable") > -1)
				return (useLarge ? R.drawable.lg_usb : R.drawable.usb);
			else {
				OpenPath[] lists = null;
				if(!file.requiresThread())
					lists = file.list();
			
				if(file.canRead() && lists != null && lists.length > 0)
					return (useLarge ? R.drawable.lg_folder_full : R.drawable.folder_full);
				else
					return (useLarge ? R.drawable.lg_folder : R.drawable.folder);
			}
		} else if(ext.equalsIgnoreCase("doc") || ext.equalsIgnoreCase("docx")) {
			return (useLarge ? R.drawable.lg_doc : R.drawable.doc);
			
		} else if(ext.equalsIgnoreCase("xls")  || 
				  ext.equalsIgnoreCase("xlsx") ||
				  ext.equalsIgnoreCase("xlsm")) {
			return (useLarge ? R.drawable.lg_excel : R.drawable.excel);
			
		} else if(ext.equalsIgnoreCase("ppt") || ext.equalsIgnoreCase("pptx")) {
			return (useLarge ? R.drawable.lg_powerpoint : R.drawable.powerpoint);
			
		} else if(ext.equalsIgnoreCase("zip") || ext.equalsIgnoreCase("gzip")) {
			return (R.drawable.zip);
			
		} else if (ext.equalsIgnoreCase("rar")) {
			return (R.drawable.rar);
			
		//} else if(ext.equalsIgnoreCase("apk")) {
		//	return (R.drawable.apk);
			
		} else if(ext.equalsIgnoreCase("pdf")) {
			return (useLarge ? R.drawable.lg_pdf : R.drawable.pdf);
			
		} else if(ext.equalsIgnoreCase("xml") || ext.equalsIgnoreCase("html")) {
			return (useLarge ? R.drawable.lg_xml_html : R.drawable.xml_html);
			
		} else if(ext.equalsIgnoreCase("mp3") || ext.equalsIgnoreCase("wav") ||
				  ext.equalsIgnoreCase("wma") || ext.equalsIgnoreCase("m4p") ||
				  ext.equalsIgnoreCase("m4a") || ext.equalsIgnoreCase("ogg")) {
			return (useLarge ? R.drawable.lg_music : R.drawable.music);
		} else if(ext.equalsIgnoreCase("jpeg")|| ext.equalsIgnoreCase("png") ||
				  ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("gif") ||
				  ext.equalsIgnoreCase("bmp"))
			return (useLarge ? R.drawable.lg_photo : R.drawable.photo);
		else if(ext.equalsIgnoreCase("apk"))
			return (R.drawable.apk);
		else if(ext.equalsIgnoreCase("mp4") || 
				  ext.equalsIgnoreCase("3gp") || 
				  ext.equalsIgnoreCase("avi") ||
				  ext.equalsIgnoreCase("webm")|| 
				  ext.equalsIgnoreCase("m4v"))
			return (useLarge ? R.drawable.lg_movie : R.drawable.movie);
		else if(OpenFTP.class.equals(file.getClass()) && file.isDirectory())
			return (useLarge ? R.drawable.lg_ftp : R.drawable.ftp);
		else
			return (useLarge ? R.drawable.lg_unknown : R.drawable.unknown);
	}
	
	public static void setContext(Context c) { mContext = c; }
	
	public static Bitmap getThumbnailCache(String name, int w, int h) {
		String cacheName = getCacheFilename(name, w, h);
		if(mCacheMap.get(cacheName) != null)
			return mCacheMap.get(cacheName);
		else {
			File f = mContext.getFileStreamPath(cacheName);
			if(f.exists())
				return BitmapFactory.decodeFile(f.getPath());
		}
		return null;
	}
	private static void putThumbnailCache(String cacheName, Bitmap value)
	{
		//String cacheName = getCacheFilename(name, w, h);
		mCacheMap.put(cacheName, value);
	}
	
	private static String getCacheFilename(String path, int w, int h)
	{
		return w + "x" + h + "_" + path.replaceAll("[^A-Za-z0-9]", "-") + ".png";
	}
	
	public static SoftReference<Bitmap> generateThumb(final OpenPath file, int mWidth, int mHeight) { return generateThumb(file, mWidth, mHeight, true, true, mContext); }
	public static SoftReference<Bitmap> generateThumb(final OpenPath file, int mWidth, int mHeight, final boolean readCache, final boolean writeCache) { return generateThumb(file, mWidth, mHeight, readCache, writeCache, mContext); }
	public static SoftReference<Bitmap> generateThumb(final OpenPath file, int mWidth, int mHeight, Context context) { return generateThumb(file, mWidth, mHeight, true, true, context); }
	public static SoftReference<Bitmap> generateThumb(final OpenPath file, int mWidth, int mHeight, final boolean readCache, final boolean writeCache, Context context)
	{
		final boolean useLarge = mWidth > 72;
		//SoftReference<Bitmap> mThumb = null;
		Bitmap bmp = null;
		//final Handler mHandler = next.Handler;
		
		if(file.requiresThread())
		{
			if(context != null)
				return new SoftReference<Bitmap>(BitmapFactory.decodeResource(context.getResources(), getDefaultResourceId(file, mWidth, mHeight)));
		}
		
		String path = file.getPath();
		
		if((bmp = getThumbnailCache(path, mWidth, mHeight)) != null)
			return new SoftReference<Bitmap>(bmp);
		
		String mCacheFilename = getCacheFilename(path, mWidth, mHeight);
		
		//we already loaded this thumbnail, just return it.
		if (mCacheMap.get(mCacheFilename) != null) 
			return new SoftReference<Bitmap>(mCacheMap.get(mCacheFilename));
		if(readCache && bmp == null)
			bmp = loadThumbnail(mCacheFilename);
		
		if(bmp == null)
		{
			Boolean valid = false;
			if (file.getClass().equals(OpenMediaStore.class))
			{
				OpenMediaStore om = (OpenMediaStore)file;
				BitmapFactory.Options opts = new BitmapFactory.Options();
				opts.inSampleSize = 1;
				//opts.outWidth = mWidth;
				//opts.outHeight = mHeight;
				int kind = mWidth > 96 ? MediaStore.Video.Thumbnails.MINI_KIND : MediaStore.Video.Thumbnails.MICRO_KIND;
				try {
					if(om.getParent().getName().equals("Photos"))
						bmp = MediaStore.Images.Thumbnails.getThumbnail(
									mContext.getContentResolver(),
									om.getMediaID(), kind, opts
								);
					else // if(om.getParent().getName().equals("Videos"))
						bmp = MediaStore.Video.Thumbnails.getThumbnail(
									mContext.getContentResolver(),
									om.getMediaID(), kind, opts
								);
				} catch(Exception e) {
					Logger.LogWarning("Couldn't get MediaStore thumbnail.", e);
				}
				if(bmp != null) {
					//Logger.LogDebug("Bitmap is " + bmp.getWidth() + "x" + bmp.getHeight() + " to " + mWidth + "x" + mHeight);
					valid = true;
				} else Logger.LogError("Unable to create MediaStore thumbnail.");
			}
			if (!valid && isAPKFile(file.getName()))
			{
				//Logger.LogInfo("Getting apk icon for " + file.getName());
				JarFile apk = null;
				InputStream in = null;
				try {
					apk = new JarFile(((OpenFile)file).getFile());
					JarEntry icon = apk.getJarEntry("res/drawable-hdpi/icon.apk");
					if(icon != null && icon.getSize() > 0) {
						in = apk.getInputStream(icon);
						bmp = BitmapFactory.decodeStream(in);
						in.close();
						in = null;
					}
					if(!valid) {
						PackageManager man = mContext.getPackageManager();
						PackageInfo pinfo = man.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
						if(pinfo != null)
						{
							ApplicationInfo ainfo = pinfo.applicationInfo;
							if(Build.VERSION.SDK_INT >= 8)
								ainfo.publicSourceDir = ainfo.sourceDir = file.getPath();
							Drawable mIcon = ainfo.loadIcon(man);
							if(mIcon != null)
								bmp = ((BitmapDrawable)mIcon).getBitmap();
						}
					}
					if(!valid) {
						Logger.LogWarning("Couldn't get icon for " + file.getAbsolutePath());
						String iconName = "icon"; //getIconName(apk, file);
						if(iconName.indexOf(" ") > -1)
							iconName = "icon";
						for(String s : new String[]{"drawable-mdpi","drawable","drawable-hdpi","drawable-ldpi"})
						{
							icon = apk.getJarEntry("res/" + s + "/" + iconName + ".png");
							if(icon != null && icon.getSize() > 0)
							{
								in = apk.getInputStream(icon);
								bmp = BitmapFactory.decodeStream(in);
								in.close();
								in = null;
							}
						}
					}
					if(bmp == null)
						bmp = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.apk);
				} catch(IOException ix) {
					Logger.LogError("Invalid APK: " + file.getPath(), ix);
				}
				finally {
					try {
						if(apk != null)
							apk.close();
					} catch(IOException nix) {
						Logger.LogError("Error closing APK while handling invalid APK exception.", nix);
					}
					try {
						if(in != null)
							in.close();
					} catch(IOException nix) {
						Logger.LogError("Error closing input stream while handling invalid APK exception.", nix);
					}
				}
			} else if (!valid && isImageFile(file.getPath())) {
				long len_kb = file.length() / 1024;
				
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.outWidth = mWidth;
				options.outHeight = mHeight;
										
				if (len_kb > 500 && len_kb < 2000) {
					options.inSampleSize = 16;
					options.inPurgeable = true;						
					bmp = BitmapFactory.decodeFile(file.getPath(), options);
										
				} else if (len_kb >= 2000) {
					options.inSampleSize = 32;
					options.inPurgeable = true;
					bmp = BitmapFactory.decodeFile(file.getPath(), options);
									
				} else if (len_kb <= 500) {
					options.inPurgeable = true;
					bmp = BitmapFactory.decodeFile(file.getPath());
					
					if (bmp == null) 
						bmp = BitmapFactory.decodeResource(mContext.getResources(), useLarge ? R.drawable.lg_photo : R.drawable.photo);
					
				}
			} else if (bmp == null && file.getClass().equals(OpenFile.class))
			{
				if(file.isDirectory())
					bmp = BitmapFactory.decodeResource(mContext.getResources(), useLarge ? R.drawable.lg_folder : R.drawable.folder);
				else
					bmp = BitmapFactory.decodeResource(mContext.getResources(), useLarge ? R.drawable.lg_unknown : R.drawable.unknown);
			}
		}
		
		if(bmp != null && bmp.getWidth() > mWidth)
		{
			int h = (int) Math.floor(mWidth * ((double)bmp.getHeight() / (double)bmp.getWidth())); 
			bmp = Bitmap.createScaledBitmap(bmp, mWidth, h, false);
		}
		
		if(bmp != null)
		{
			if(writeCache)
				saveThumbnail(mCacheFilename, bmp);
			mCacheMap.put(mCacheFilename, bmp);
		}
		//Logger.LogDebug("Created " + bmp.getWidth() + "x" + bmp.getHeight() + " thumb (" + mWidth + "x" + mHeight + ")");
		return new SoftReference<Bitmap>(bmp);
	}
	
	private static Bitmap loadThumbnail(String file)
	{
		if(mContext != null)
			return BitmapFactory.decodeFile(file);
		return null;
	}
	
	private static void saveThumbnail(String file, Bitmap bmp)
	{
		FileOutputStream os = null;
		try {
			os = mContext.openFileOutput(file, 0);
			bmp.compress(CompressFormat.PNG, 100, os);
		} catch(IOException e) {
			Logger.LogError("Unable to save thumbnail for " + file, e);
		} finally {
			if(os != null)
				try {
					os.close();
				} catch (IOException e) { }
		}
	}
	
	
	private void sendThumbBack(SoftReference<Bitmap> mThumb, String path)
	{
		final Bitmap d = mThumb.get();
		new BitmapDrawable(d).setGravity(Gravity.CENTER);
		mCacheMap.put(path, d);
		
		mHandler.post(new Runnable() {
			
			public void run() {
				Message msg = mHandler.obtainMessage();
				msg.obj = d;
				msg.sendToTarget();
			}
		});
	}
	
	private static boolean isImageFile(String file) {
		String ext = file.substring(file.lastIndexOf(".") + 1);
		
		if (ext.equalsIgnoreCase("png") || ext.equalsIgnoreCase("jpg") ||
			ext.equalsIgnoreCase("jpeg")|| ext.equalsIgnoreCase("gif") ||
			ext.equalsIgnoreCase("tiff")|| ext.equalsIgnoreCase("tif"))
			return true;
		
		return false;
	}
	
	private static boolean isAPKFile(String file) {
		String ext = file.substring(file.lastIndexOf(".") + 1);
		
		if (ext.equalsIgnoreCase("apk"))
			return true;
		
		return false;
	}
	
	private static boolean isVideoFile(String path)
	{
		String ext = path.substring(path.lastIndexOf(".") + 1);
		if(ext.equalsIgnoreCase("mp4") || 
			  ext.equalsIgnoreCase("3gp") || 
			  ext.equalsIgnoreCase("avi") ||
			  ext.equalsIgnoreCase("webm") || 
			  ext.equalsIgnoreCase("m4v"))
			return true;
		return false;
	}

	public static void flushCache() {
		//Logger.LogInfo("Flushing" + mCacheMap.size() + " from memory & " + mContext.fileList().length + " from disk.");
		mCacheMap.clear();
		for(String s : mContext.fileList())
			mContext.deleteFile(s);
	}

	public static boolean hasContext() {
		return mContext != null;
	}
	

	
	

}
