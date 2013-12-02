package com.wanikani.androidnotifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.content.Context;
import android.os.AsyncTask;

public class FileDownloadTask extends AsyncTask<String, Integer, File>{
	
	public static interface Listener {
		
		public void setProgress (int percentage);
		
		public void done (File file);
	}

	private Context ctxt;
	
	private Listener listener;
	
	private String prefix;
	
	private boolean cancelled;
	
	public FileDownloadTask (Context ctxt, String prefix, Listener listener)
	{
		this.ctxt = ctxt;
		this.prefix = prefix;
		this.listener = listener;
	}
	
	public void cancel ()
	{
		cancelled = true;
	}

	@Override
	public File doInBackground (String... url)
	{		
		HttpURLConnection conn;
		File outdir, outf;
		InputStream is;
		OutputStream os;
		byte buf [];
		int read, size, br, delta, next;
		boolean ok;
		
		ok = false;
		outf = null;
		is = null;
		os = null;
		conn = null;
		try {
			conn = (HttpURLConnection) new URL (url [0]).openConnection ();
			conn.connect ();
			size = conn.getContentLength ();
			if (size < 0) /* Arbitrary but nothing wrong if wrong */
				size = 100000000;
			
			is = conn.getInputStream ();
			outdir = ctxt.getFilesDir ();
			if (outdir == null)
				throw new IOException ("Can't open output dir");
			
			outf = new File (outdir, prefix + System.currentTimeMillis ());
			os = new FileOutputStream (outf);
			
			buf = new byte [8192];
			read = 0;
			delta = next = size / 10;
			while (!cancelled) {
				br = is.read (buf);
				if (br < 0)
					break;
				os.write (buf, 0, br);
				read += br;
				
				if (read > next)
					publishProgress (read * 100 / size);
				
				while (next < read)
					next += delta;
			} 
			
			ok = true;
			
		} catch (IOException e) {
			/* implicitly set ok = false */
		} finally {
			try {
				if (is != null)
					is.close ();
			} catch (IOException e) {
				/* empty */
			}
			try {
				if (os != null)
					os.close ();
			}  catch (IOException e) {
				ok = false;
			}
			if (cancelled || !ok && outf != null)
				outf.delete ();
		}		
		
		return ok ? outf : null;
	}
	
	@Override
	public void onProgressUpdate (Integer... percentage)
	{
		if (!cancelled)
			listener.setProgress (percentage [0]);
	}
	
	@Override
	public void onPostExecute (File file)
	{
		if (!cancelled)
			listener.done (file);
	}
}
