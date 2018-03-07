package org.yaxim.androidclient;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.TextView;

import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Pictures {
    private static final String PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/org.yaxim.androidclient/pics/";
    private static final int MAXSIZE = 10 * 1024 * 1024;
    private static Pattern linkPattern = Pattern.compile("(https?)+://[-a-zA-Z0-9+/~_|!:,.;]*(png|jpg|jpeg|gif|webp)", Pattern.CASE_INSENSITIVE);
    private static final List<String> links = new ArrayList<String>();

    public static void loadPicture(final Activity activity, final TextView tv) {
		SpannableStringBuilder ssb = new SpannableStringBuilder(tv.getText());

        Matcher m = linkPattern.matcher(ssb);
        while (m.find()) {
            final String url = ssb.subSequence(m.start(), m.end()).toString();
            String file = url.substring(url.lastIndexOf("/")+1, url.length());
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                messageDigest.reset();
                messageDigest.update(url.getBytes());
                byte[] resultByte = messageDigest.digest();
                BigInteger bigInt = new BigInteger(1,resultByte);
                file = bigInt.toString(16);
            } catch (NoSuchAlgorithmException ignored) {}

            final String fname = PATH + file;
            final File myFile = new File(fname);
            if (!myFile.exists()) {
                if (links.contains(fname)) return;
                else links.add(fname);

                new Thread() {
                    @Override
                    public void run() {
                        try {
                            File folder = new File(PATH);
                            if (!folder.exists()) folder.mkdirs();

                            URL obj = new URL(url);
                            URLConnection conn = obj.openConnection();
                            int length = conn.getContentLength();
                            if (length < MAXSIZE) {
                                BufferedInputStream in = new BufferedInputStream(new URL(url).openStream());
                                FileOutputStream fout = new FileOutputStream(fname);

                                final byte data[] = new byte[1024];
                                int count;
                                while ((count = in.read(data, 0, 1024)) != -1) {
                                    fout.write(data, 0, count);
                                }
                                in.close();
                                fout.close();
                            }
                        } catch (Exception ignored) { }
                    }
                }.start();
            } else {
                Bitmap bitmap;
                DisplayMetrics metrics = new DisplayMetrics();
                activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

                int maxWidth = metrics.widthPixels;
                int maxHeight = metrics.heightPixels;

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(fname, options);

                options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight);
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeFile(fname, options);

                if (bitmap != null) {
                    int width = bitmap.getWidth();
                    if (width > maxWidth)  {
                        double k = (double)width/(double)maxWidth;
                        int h = (int) (bitmap.getHeight()/k);
                        bitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, h, true);
                    }

                    ssb.append("\n\n");
                    int start = ssb.length();
                    ssb.append(" \n");

                    ssb.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(View view) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(Uri.fromFile(myFile), "image/*");
                            activity.startActivity(intent);
                        }
                    }, start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.setSpan(new ImageSpan(activity, bitmap, ImageSpan.ALIGN_BASELINE), start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tv.setText(ssb);
                }
            }
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int maxWidth, int maxHeight) {
        final int width = options.outWidth;
        final int height = options.outHeight;
        int inSampleSize = 1;

        if (width > maxWidth || height > maxHeight) {
            final int halfWidth = width / 2;
            final int halfHeight = height / 2;

            while (((halfWidth / inSampleSize) > maxWidth) || ((halfHeight / inSampleSize) > maxHeight)) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}
