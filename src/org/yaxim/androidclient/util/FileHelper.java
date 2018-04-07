package org.yaxim.androidclient.util;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by georg on 12/27/17.
 */

public class FileHelper {
	private static final int IMAGE_SIZE = 1920;
	private static final int IMAGE_QUALITY = 80;

	/**
	 * Get a file path from a Uri. This will get the the path for Storage Access
	 * Framework Documents, as well as the _data field for the MediaStore and
	 * other file-based ContentProviders.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @author paulburke
	 */
	@SuppressLint("NewApi")
	public static String getPath(final Context context, final Uri uri) {

		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

		// DocumentProvider
		if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
			// ExternalStorageProvider
			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

//				if ("primary".equalsIgnoreCase(type)) {
				return Environment.getExternalStorageDirectory() + "/" + split[1];
//				}
			}
			// DownloadsProvider
			else if (isDownloadsDocument(uri)) {

				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(
						Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

				return getDataColumn(context, contentUri, null, null);
			}
			// MediaProvider
			else if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}

				final String selection = "_id=?";
				final String[] selectionArgs = new String[] {
						split[1]
				};

				return getDataColumn(context, contentUri, selection, selectionArgs);
			}
		}
		// MediaStore (and general)
		else if ("content".equalsIgnoreCase(uri.getScheme())) {

			// Return the remote address
			if (isGooglePhotosUri(uri))
				return uri.getLastPathSegment();

			return getDataColumn(context, uri, null, null);
		}
		// File
		else if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}

		return null;
	}

	/**
	 * Get the value of the data column for this Uri. This is useful for
	 * MediaStore Uris, and other file-based ContentProviders.
	 *
	 * @param context The context.
	 * @param uri The Uri to query.
	 * @param selection (Optional) Filter used in the query.
	 * @param selectionArgs (Optional) Selection arguments used in the query.
	 * @return The value of the _data column, which is typically a file path.
	 */
	public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {

		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = {
				column
		};

		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
					null);
			if (cursor != null && cursor.moveToFirst()) {
				final int index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is ExternalStorageProvider.
	 */
	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 */
	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 */
	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is Google Photos.
	 */
	public static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}

	public static File createImageFile(Context ctx) {
		// Create an image file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String imageFileName = timeStamp + "_";
		File storageDir = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			storageDir = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
		}
		if (storageDir == null)
			storageDir = new File(Environment.getExternalStorageDirectory() + "/DCIM/");
		try {
			File image = File.createTempFile(
					imageFileName,  /* prefix */
					".jpg",         /* suffix */
					storageDir      /* directory */
			);
			return image;
		} catch (Exception e) {
			Log.d("yaxim.FileHelper", e.getLocalizedMessage());
			e.printStackTrace();
			return null;
		}
	}

	public static int getExifOrientation(Context ctx, Uri path) {
		try {
			ExifInterface exif;
			exif = new ExifInterface(getPath(ctx, path));
			return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
		} catch (Exception e) {
			Log.e("yaxim.FileHelper", "getExifOrientation: " + e.getLocalizedMessage());
			e.printStackTrace();
		}
		return 1; // default to "no rotation"
	}
	public static int getExifRotation(Context ctx, Uri path) {
		switch (getExifOrientation(ctx, path)) {
		case ExifInterface.ORIENTATION_ROTATE_90:
			return 90;
		case ExifInterface.ORIENTATION_ROTATE_180:
			return 180;
		case ExifInterface.ORIENTATION_ROTATE_270:
			return 270;
		default:
			return 0;
		}
	}

	public static byte[] shrinkPicture(Context ctx, Uri path, long size_limit) {
		try {
			InputStream is = ctx.getContentResolver().openInputStream(path);
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;
			BitmapFactory.decodeStream(is, null, opts);
			if (!opts.outMimeType.contains("jpeg") || opts.outHeight < 0 || opts.outWidth < 0)
				return null;
			int current_size = (opts.outWidth > opts.outHeight)? opts.outWidth : opts.outHeight;
			int factor = (int)Math.ceil((double)current_size/ IMAGE_SIZE);
			is.close();
			int rotation = getExifRotation(ctx, path);
			ByteArrayOutputStream baos;
			do {
				Log.d("yaxim.FileHelper", "Shrinking image from " + opts.outWidth + "*" + opts.outHeight + " by factor " + factor + "...");
				opts.inSampleSize = factor;
				opts.inJustDecodeBounds = false;
				is = ctx.getContentResolver().openInputStream(path);
				Bitmap result = BitmapFactory.decodeStream(is, null, opts);
				is.close();
				if (rotation != 0) {
					Log.d("yaxim.FileHelper", "Rotating image by " + rotation + "°");
					Matrix matrix = new Matrix();
					matrix.postRotate(rotation);
					result = Bitmap.createBitmap(result, 0, 0, result.getWidth(), result.getHeight(),
							matrix, true);
				}
				baos = new ByteArrayOutputStream();
				result.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, baos);
				factor++;
			} while (size_limit > 0 && baos.size() > size_limit);
			return baos.toByteArray();
		} catch (Exception e) {
			return null;
		}
	}

	public static FileInfo getFileInfo(Context ctx, Uri path) {
		FileInfo fi = null;
		if (path.getScheme().equals("file")) {
			File f = new File(path.getPath());
			return new FileInfo(URLConnection.guessContentTypeFromName(path.toString()),
					path.getLastPathSegment(), f.length());
		}
		ContentResolver cr = ctx.getContentResolver();
		Cursor c = cr.query(path, null, null, null, null);
		/*
		 * Get the column indexes of the data in the Cursor,
		 * move to the first row in the Cursor, get the data,
		 * and display it.
		 */
		int nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
		int sizeIndex = c.getColumnIndex(OpenableColumns.SIZE);
		int dataIndex = c.getColumnIndex(MediaStore.Images.Media.DATA);
		if (c.moveToFirst()) {
			fi = new FileInfo(cr.getType(path), c.getString(nameIndex), c.getLong(sizeIndex));
			if (fi.size == 0) {
				String filepath = c.getString(dataIndex);
				File f = new File(filepath);
				fi = new FileInfo(URLConnection.guessContentTypeFromName(filepath),
						f.getName(), f.length());
			}
		}
		c.close();
		return fi;
	}

	public static class FileInfo {
		public String mimeType;
		public String displayName;
		public long size;

		public FileInfo(String mimeType, String displayName, long size) {
			this.mimeType = mimeType;
			this.displayName = displayName;
			this.size = size;
		}
	}
}
