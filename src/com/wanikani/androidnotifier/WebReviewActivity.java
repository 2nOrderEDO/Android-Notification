package com.wanikani.androidnotifier;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieSyncManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/* 
 *  Copyright (c) 2013 Alberto Cuda
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * This activity allows the user to perform its reviews through an integrated
 * browser. The only reason we need this (instead of just spawning an external
 * browser) is that we also display a minimal keyboard, that interacts with WK scripts
 * to compose kanas. Ordinarily, in fact, Android keyboards do not behave correctly.
 * <p>
 * The keyboard is displayed only when needed, so we need to check whether the
 * page contains a <code>user_response</code> text box, and it is enabled.
 * In addition, to submit the form, we simulate a click on the <code>option-submit</code>
 * button. Since the keyboard hides the standard controls (in particular the
 * info ("did you know...") balloons), we hide the keyboard when the user enters 
 * his/her response. 
 * <p>
 * To accomplish this, we register a JavascriptObject (<code>wknKeyboard</code>) and inject
 * a javascript to check how the page looks like. If the keyboard needs to be shown,
 * it calls its <code>show</code> (vs. <code>hide</code>) method.
 * The JavascriptObject is implemented by @link WebReviewActivity.WKNKeyboard.
 */
public class WebReviewActivity extends Activity {
	
	/**
	 * This class is barely a container of all the strings that should match with the
	 * WaniKani portal. Hopefully none of these will ever be changed, but in case
	 * it does, here is where to look for.
	 */
	public static class WKConfig {
		
		/** New review start page. This is the start page when client side reviews will be deployed */
		static final String CURRENT_REVIEW_START = "http://www.wanikani.com/review/session";

		/** Review start page. Of course must be inside of @link {@link #REVIEW_SPACE} */
		static final String CURRENT_LESSON_START = "http://www.wanikani.com/lesson/session";

		/** HTML id of the textbox the user types its answer in (reviews, client-side) */
		static final String ANSWER_BOX = "user-response";

		/** HTML id of the textbox the user types its answer in (lessons) */
		static final String LESSON_ANSWER_BOX_JP = "translit";
		
		/** HTML id of the textbox the user types its answer in (lessons) */
		static final String LESSON_ANSWER_BOX_EN = "lesson_user_response";

		/** HTML id of the submit button */
		static final String SUBMIT_BUTTON = "option-submit";

		/** HTML id of the lessons review form */
		static final String LESSONS_REVIEW_FORM = "new_lesson";
		
		/** HTML id of the lessons quiz */
		static final String QUIZ = "quiz";
		
		/** Any object on the lesson pages */
		static final String LESSONS_OBJ = "nav-lesson";
		
		/** Reviews div */
		static final String REVIEWS_DIV = "reviews";		
	};
	
	/**
	 * The listener attached to the ignore button tip message.
	 * When the user taps the ok button, we write on the property
	 * that it has been acknowleged, so it won't show up any more. 
	 */
	private class OkListener implements DialogInterface.OnClickListener {
		
		@Override
		public void onClick (DialogInterface ifc, int which)
		{
			SettingsActivity.setIgnoreButtonMessage (WebReviewActivity.this, true);
		}		
	}
	

	/**
	 * The listener that receives events from the mute buttons.
	 */
	private class MuteListener implements View.OnClickListener {
		
		@Override
		public void onClick (View w)
		{
			SettingsActivity.toggleMute (WebReviewActivity.this);
			applyMuteSettings ();
		}
	}

	/**
	 * Web view controller. This class is used by @link WebView to tell whether
	 * a link should be opened inside of it, or an external browser needs to be invoked.
	 * Currently, I will let all the pages inside the <code>/review</code> namespace
	 * to be opened here. Theoretically, it could even be stricter, and use
	 * <code>/review/session</code>, but that would be prevent the final summary
	 * from being shown. That page is useful, albeit not as integrated with the app as
	 * the other pages.
	 */
	private class WebViewClientImpl extends WebViewClient {
				
		/**
		 * Called to check whether a link should be opened in the view or not.
		 * We also display the progress bar.
		 * 	@param view the web view
		 *  @url the URL to be opened
		 */
	    @Override
	    public boolean shouldOverrideUrlLoading (WebView view, String url) 
	    {
	        view.loadUrl (url);

	        return true;
	    }
		
	    /**
	     * Called when something bad happens while accessing the resource.
	     * Show the splash screen and give some explanation (based on the <code>description</code>
	     * string).
	     * 	@param view the web view
	     *  @param errorCode HTTP error code
	     *  @param description an error description
	     *  @param failingUrl error
	     */
	    public void onReceivedError (WebView view, int errorCode, String description, String failingUrl)
	    {
	    	String s;
	    	
	    	s = getResources ().getString (R.string.fmt_web_review_error, description);
	    	splashScreen (s);
	    	bar.setVisibility (View.GONE);
	    }

		@Override  
	    public void onPageStarted (WebView view, String url, Bitmap favicon)  
	    {  
	        bar.setVisibility (View.VISIBLE);
		}
	
		/**
	     * Called when a page finishes to be loaded. We hide the progress bar
	     * and run the initialization javascript that shows the keyboard, if needed.
	     * In addition, if this is the initial page, we check whether Viet has
	     * deployed the client-side review system
	     */
		@Override  
	    public void onPageFinished (WebView view, String url)  
	    {  
			bar.setVisibility (View.GONE);

			if (url.startsWith ("http"))
				wv.js (JS_INIT_KBD);			
	    }
	}
	
	/**
	 * An additional webclient, that receives a few callbacks that a simple 
	 * {@link WebChromeClient} does not intecept. 
	 */
	private class WebChromeClientImpl extends WebChromeClient {
	
		/**
		 * Called as the download progresses. We update the progress bar.
		 * @param view the web view
		 * @param progress progress percentage
		 */
		@Override	
		public void onProgressChanged (WebView view, int progress)
		{
			bar.setProgress (progress);
		}		
	};

	/**
	 * A small job that hides, shows or iconizes the keyboard. We need to implement this
	 * here because {@link WebReviewActibity.WKNKeyboard} gets called from a
	 * javascript thread, which is not necessarily an UI thread.
	 * The constructor simply calls <code>runOnUIThread</code> to make sure
	 * we hide/show the views from the correct context.
	 */
	private class ShowHideKeyboard implements Runnable {
		
		/** New state to enter */
		KeyboardStatus kbstatus;
		
		/**
		 * Constructor. It also takes care to schedule the invokation
		 * on the UI thread, so all you have to do is just to create an
		 * instance of this object
		 * @param kbstatus the new keyboard status to enter
		 */
		ShowHideKeyboard (KeyboardStatus kbstatus)
		{
			this.kbstatus = kbstatus;
			
			runOnUiThread (this);
		}
		
		/**
		 * Hides/shows the keyboard. Invoked by the UI thread.
		 * As a side effect, we update the {@link WebReviewActivity#flushCaches}
		 * bit.
		 */
		public void run ()
		{	
			kbstatus.apply (WebReviewActivity.this);
			if (kbstatus.isRelevantPage ())
				reviewsSession ();			
		}
		
		private void reviewsSession ()
		{
			flushCaches = true;
			CookieSyncManager.getInstance ().sync ();
		}
		
	}
	
	/**
	 * This class implements the <code>wknKeyboard</code> javascript object.
	 * It implements the @link {@link #show} and {@link #hide} methods. 
	 */
	private class WKNKeyboard {
		
		/**
		 * Called by javascript when the keyboard should be shown.
		 */
		@JavascriptInterface
		public void show ()
		{
			new ShowHideKeyboard (KeyboardStatus.REVIEWS_MAXIMIZED);
		}

		/**
		 * Called by javascript when the keyboard should be shown, using
		 * lessons layout.
		 */
		@JavascriptInterface
		public void showLessons ()
		{
			new ShowHideKeyboard (KeyboardStatus.LESSONS_MAXIMIZED);
		}

		/**
		 * Called by javascript when the keyboard should be shown, using
		 * new lessons layout.
		 */
		@JavascriptInterface
		public void showLessonsNew ()
		{
			new ShowHideKeyboard (KeyboardStatus.LESSONS_MAXIMIZED_NEW);
		}

		/**
		 * Called by javascript when the keyboard should be hidden.
		 */
		@JavascriptInterface
		public void hide ()
		{
			new ShowHideKeyboard (KeyboardStatus.INVISIBLE);
		}

		/**
		 * Called by javascript when the keyboard should be iconized.
		 */
		@JavascriptInterface
		public void iconize ()
		{
			new ShowHideKeyboard (KeyboardStatus.REVIEWS_ICONIZED);
		}

		/**
		 * Called by javascript when the keyboard should be iconized (lessons mode).
		 */
		@JavascriptInterface
		public void iconizeLessons ()
		{
			new ShowHideKeyboard (KeyboardStatus.LESSONS_ICONIZED);
		}	
	}
	
	/**
	 * Our implementation of a menu listener. We listen for configuration changes. 
	 */
	private class MenuListener extends MenuHandler.Listener {
		
		public MenuListener ()
		{
			super (WebReviewActivity.this);
		}
		
		/**
		 * The dashboard listener exits the activity
		 */
		public void dashboard ()
		{
			Intent i;
			
			i = new Intent (WebReviewActivity.this, MainActivity.class);
			i.setAction (Intent.ACTION_MAIN);
			i.addFlags (Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
			
			startActivity (i);

			finish ();
		}
		
		/**
		 * Ignore button
		 */
		public void ignore ()
		{
			showIgnoreButtonMessage ();
			keyboard.ignore ();
		}
		
		/**
		 * Toggle override fonts
		 */
		@Override
		public void fonts ()
		{
			keyboard.overrideFonts ();
		}
	}
	
	/**
	 * Keyboard visiblity status.
	 */
	enum KeyboardStatus {
		
		/** Keyboard visible, all keys visible */
		REVIEWS_MAXIMIZED {
			 public void apply (WebReviewActivity wav) { wav.show (this); }

			 public void iconize (WebReviewActivity wav) { REVIEWS_ICONIZED.apply (wav); }

 			 public SettingsActivity.Keyboard getKeyboard (WebReviewActivity wav)
			 {
				 return SettingsActivity.getReviewsKeyboard (wav);
			 }

 			 public boolean canMute ()
 			 {
 				 return true;
 			 }
 			
 			public boolean isMuteEmbedded ()
 			{
 				return true;
 			}
 			
 			public boolean hasEnter (WebReviewActivity wav)
 			{
 				return SettingsActivity.getEnter (wav);
 			}
		},
		
		/** Keyboard visible, all keys but ENTER visible */
		LESSONS_MAXIMIZED {
			public void apply (WebReviewActivity wav) { wav.show (this); } 
			
			public void iconize (WebReviewActivity wav) { LESSONS_ICONIZED.apply (wav); }

			public SettingsActivity.Keyboard getKeyboard (WebReviewActivity wav)
			{
				return SettingsActivity.getLessonsKeyboard (wav);				
			}
		},

		/** Keyboard visible, all keys but ENTER visible */
		LESSONS_MAXIMIZED_NEW {
			public void apply (WebReviewActivity wav) { wav.show (this); } 
			
			public void iconize (WebReviewActivity wav) { LESSONS_ICONIZED_NEW.apply (wav); }

			public SettingsActivity.Keyboard getKeyboard (WebReviewActivity wav)
			{
				return SettingsActivity.getReviewsKeyboard (wav);				
			}

			public boolean canMute ()
			{
				return true;
			}
			
		},

		/** Keyboard visible, just "Show" and "Enter" keys are visible */ 
		REVIEWS_ICONIZED {
			public void apply (WebReviewActivity wav) { wav.iconize (this); }
			
			public void maximize (WebReviewActivity wav) { REVIEWS_MAXIMIZED.apply (wav); }
			
			 public SettingsActivity.Keyboard getKeyboard (WebReviewActivity wav)
			 {
				 return SettingsActivity.getReviewsKeyboard (wav);
			 }

			 public boolean isIconized () { return true; }
			 
 			 public boolean canMute ()
 			 {
 				 return true;
 			 }
 			
 			 public boolean isMuteEmbedded ()
 			 {
 			 	 return false;
 			 }			 
 			 
  			public boolean hasEnter (WebReviewActivity wav)
  			{
  				return SettingsActivity.getEnter (wav);
  			}
		},

		/** Keyboard visible, just "Show" and "Enter" keys are visible, in lessons mode */ 
		LESSONS_ICONIZED {
			public void apply (WebReviewActivity wav) { wav.iconize (this); }
			
			public void maximize (WebReviewActivity wav) { LESSONS_MAXIMIZED.apply (wav); }
			
			public boolean isIconized () { return true; }

			public SettingsActivity.Keyboard getKeyboard (WebReviewActivity wav)
			{
				return SettingsActivity.getLessonsKeyboard (wav);				
			}			
		},
		
		/** Keyboard visible, just "Show" and "Enter" keys are visible, in lessons mode */ 
		LESSONS_ICONIZED_NEW {
			public void apply (WebReviewActivity wav) { wav.iconize (this); }
			
			public void maximize (WebReviewActivity wav) { LESSONS_MAXIMIZED_NEW.apply (wav); }
			
			public boolean isIconized () { return true; }

			public SettingsActivity.Keyboard getKeyboard (WebReviewActivity wav)
			{
				return SettingsActivity.getReviewsKeyboard (wav);				
			}
			
			public boolean canMute ()
			{
				return true;
			}
		},

		/** Keyboard invisible */
		INVISIBLE {
			public void apply (WebReviewActivity wav) { wav.hide (this); }
			
			public boolean isRelevantPage () { return false; }
			
			public SettingsActivity.Keyboard getKeyboard (WebReviewActivity wav)
			{
				return SettingsActivity.Keyboard.NATIVE;
			}
			
			public boolean backIsSafe () { return true; }
		};
		
		public abstract void apply (WebReviewActivity wav);
		
		public void maximize (WebReviewActivity wav)
		{
			/* empty */
		}
		
		public void iconize (WebReviewActivity wav)
		{
			/* empty */
		}

		public boolean isIconized ()
		{
			return false;
		}
		
		public abstract SettingsActivity.Keyboard getKeyboard (WebReviewActivity wav);
		
		public boolean isRelevantPage ()
		{
			return true;
		} 
		
		public boolean canMute ()
		{
			 return false;
		}
			
		public boolean isMuteEmbedded ()
		{
			return false;
		}
		
		public boolean hasEnter (WebReviewActivity wav)
		{
			return false;
		}

		public boolean backIsSafe ()
		{
			return false;
		}
	};
	
	private class ReaperTaskListener implements TimerThreadsReaper.ReaperTaskListener {
				
		public void reaped (int count, int total)
		{
			/* Here we could keep some stats. Currently unused */
		}		
	}

	/** The web view, where the web contents are rendered */
	FocusWebView wv;
	
	/** The view containing a splash screen. Visible when we want to display 
	 * some message to the user */ 
	View splashView;
	
	/**
	 * The view contaning the ordinary content.
	 */
	View contentView;
	
	/** A textview in the splash screen, where we can display some message */
	TextView msgw;
	
	/** The web progress bar */
	ProgressBar bar;
			
	/** The local prefix of this class */
	private static final String PREFIX = "com.wanikani.androidnotifier.WebReviewActivity.";
	
	/** Open action, invoked to start this action */
	public static final String OPEN_ACTION = PREFIX + "OPEN";
	
	/** Flush caches bundle key */
	private static final String KEY_FLUSH_CACHES = PREFIX + "flushCaches";
	
	/** Javascript to be called each time an HTML page is loaded. It hides or shows the keyboard */
	private static final String JS_INIT_KBD = 
			"var textbox, lessobj, ltextbox, reviews;" +
			"textbox = document.getElementById (\"" + WKConfig.ANSWER_BOX + "\"); " +
			"lessobj = document.getElementById (\"" + WKConfig.LESSONS_OBJ + "\"); " +
			"ltextbox = document.getElementById (\"" + WKConfig.LESSON_ANSWER_BOX_JP + "\"); " +
			"reviews = document.getElementById (\"" + WKConfig.REVIEWS_DIV + "\");" +
			"quiz = document.getElementById (\"" + WKConfig.QUIZ + "\");" +
			"if (ltextbox == null) {" +
			"   ltextbox = document.getElementById (\"" + WKConfig.LESSON_ANSWER_BOX_EN + "\"); " +
			"}" +
			"if (quiz != null) {" +
			"   wknKeyboard.showLessonsNew ();" +
			"} else if (textbox != null && !textbox.disabled) {" +
			"   wknKeyboard.show (); " +
			"} else if (ltextbox != null) {" +
			"   wknKeyboard.showLessons ();" +
			"} else if (lessobj != null) {" +
			"   wknKeyboard.iconizeLessons ();" +
			"} else {" +
			"	wknKeyboard.hide ();" +			
			"}" +
			"if (reviews != null) {" +
			"   reviews.style.overflow = \"visible\";" +
			"} ";
	
	/** The threads reaper */
	TimerThreadsReaper reaper;
	
	/** Thread reaper task */
	TimerThreadsReaper.ReaperTask rtask;
	
	/** The current keyboard status */
	protected KeyboardStatus kbstatus;
	
	/** The mute drawable */
	private Drawable muteDrawable;
	
	/** The sound drawable */
	private Drawable notMutedDrawable;
	
	/** The menu handler */
	private MenuHandler mh;
	
	/** Set if visible */
	boolean visible;
	
	/** Set if we have reviewed or had some lessons, so caches should be flushed */
	private boolean flushCaches;
	
	/** Is mute enabled */
	private boolean isMuted;
	
	/** The current keyboard */
	private Keyboard keyboard;
	
	/** The embedded keyboard */
	private Keyboard embeddedKeyboard;
	
	/** The native keyboard */
	private Keyboard nativeKeyboard;
	
	/** The local IME keyboard */
	private Keyboard localIMEKeyboard;
		
	/**
	 * Called when the action is initially displayed. It initializes the objects
	 * and starts loading the review page.
	 * 	@param bundle the saved bundle
	 */
	@Override
	public void onCreate (Bundle bundle) 
	{		
		super.onCreate (bundle);

		Resources res;
		
		CookieSyncManager.createInstance (this);
		setVolumeControlStream (AudioManager.STREAM_MUSIC);
		 
		mh = new MenuHandler (this, new MenuListener ());
		
		if (SettingsActivity.getFullscreen (this)) {
			getWindow ().addFlags (WindowManager.LayoutParams.FLAG_FULLSCREEN);
			requestWindowFeature (Window.FEATURE_NO_TITLE);
		}
		
		setContentView (R.layout.web_review);

		res = getResources ();
		muteDrawable = res.getDrawable(R.drawable.ic_mute);
		notMutedDrawable = res.getDrawable(R.drawable.ic_not_muted);

		kbstatus = KeyboardStatus.INVISIBLE;
		
		bar = (ProgressBar) findViewById (R.id.pb_reviews);
				
		/* First of all get references to views we'll need in the near future */
		splashView = findViewById (R.id.wv_splash);
		contentView = findViewById (R.id.wv_content);
		msgw = (TextView) findViewById (R.id.tv_message);
		wv = (FocusWebView) findViewById (R.id.wv_reviews);

		wv.getSettings ().setJavaScriptEnabled (true);
		wv.getSettings().setJavaScriptCanOpenWindowsAutomatically (true);
		wv.getSettings ().setSupportMultipleWindows (true);
		wv.getSettings ().setUseWideViewPort (true);
		wv.getSettings ().setDatabaseEnabled (true);
		wv.getSettings ().setDomStorageEnabled (true);
		wv.getSettings ().setDatabasePath (getFilesDir ().getPath () + "/wv");
		wv.addJavascriptInterface (new WKNKeyboard (), "wknKeyboard");
		wv.setScrollBarStyle (ScrollView.SCROLLBARS_OUTSIDE_OVERLAY);
		wv.setWebViewClient (new WebViewClientImpl ());
		wv.setWebChromeClient (new WebChromeClientImpl ());		
		
		wv.loadUrl (getIntent ().getData ().toString ());
		
		embeddedKeyboard = new EmbeddedKeyboard (this, wv);
		nativeKeyboard = new NativeKeyboard (this, wv);
		localIMEKeyboard = new LocalIMEKeyboard (this, wv);
		
		embeddedKeyboard.getMuteButton ().setOnClickListener (new MuteListener ());
		nativeKeyboard.getMuteButton ().setOnClickListener (new MuteListener ());
		localIMEKeyboard.getMuteButton ().setOnClickListener (new MuteListener ());

		if (SettingsActivity.getTimerReaper (this)) {
			reaper = new TimerThreadsReaper ();
			rtask = reaper.createTask (new Handler (), 2, 7000);
			rtask.setListener (new ReaperTaskListener ());
		}
	}
	
	@Override
	public void onNewIntent (Intent intent)
	{
		super.onNewIntent (intent);
		
		wv.loadUrl (intent.getData ().toString ());		
	}
	
	@Override
	protected void onResume ()
	{
		Window window;
		
		super.onResume ();

		window = getWindow ();
		if (SettingsActivity.getLockScreen (this))
			window.addFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			window.clearFlags (WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		visible = true;
		
		selectKeyboard ();
		
		applyMuteSettings ();
		
		wv.acquire ();
		
		kbstatus.apply (this);
		
		if (rtask != null)
			rtask.resume ();
	}
	
	@Override
	public void onDestroy ()
	{
		super.onDestroy ();

		mh.unregister (this);

		if (reaper != null)
			reaper.stopAll ();
		
		if (SettingsActivity.getLeakKludge (this))
			System.exit (0);
	}
	
	@Override
	protected void onSaveInstanceState (Bundle bundle)
	{
		bundle.putBoolean (KEY_FLUSH_CACHES, flushCaches);
	}
	
	@Override 
	protected void onRestoreInstanceState (Bundle bundle)
	{
		if (bundle != null && bundle.containsKey (KEY_FLUSH_CACHES))
			flushCaches = bundle.getBoolean (KEY_FLUSH_CACHES);
	}
	
	@Override
	protected void onPause ()
	{
		Intent intent;
		
		visible = false;

		super.onPause ();
		intent = new Intent (MainActivity.ACTION_REFRESH);
		intent.putExtra (MainActivity.E_FLUSH_CACHES, flushCaches);
		sendBroadcast (intent);

		/* Alert the notification service too (the main action may not be active) */
		intent = new Intent (this, NotificationService.class);
		intent.setAction (NotificationService.ACTION_NEW_DATA);
		startService (intent);
		
		setMute (false);
		
		wv.release ();
		
		if (rtask != null)
			rtask.pause ();
		
		keyboard.hide ();
	}
	
	/**
	 * Tells if calling {@link WebView#goBack()} is safe. On some WK pages we should not use it. 
	 * @return <tt>true</tt> if it is safe.
	 */
	protected boolean backIsSafe ()
	{
		String rpage, url;
		
		url = wv.getUrl ();		
		rpage = SettingsActivity.getURL (this);
		
		return kbstatus.backIsSafe () &&
				/* Need this because the reviews summary page is dangerous */
				!(url.contains (rpage) || rpage.contains (url)) &&
				!url.contains ("http://www.wanikani.com/quickview");
	}
	
	@Override
	public void onBackPressed ()
	{
		String url;
		
		url = wv.getUrl ();

		if (url == null)
			super.onBackPressed ();
		else if (url.contains ("http://www.wanikani.com/quickview"))
			wv.loadUrl (SettingsActivity.getLessonURL (this));
		else if (wv.canGoBack () && backIsSafe ())
			wv.goBack ();
		else		
			super.onBackPressed ();
	}
	
	/**
	 * Associates the menu description to the menu key (or action bar).
	 * The XML description is <code>review.xml</code>
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		getMenuInflater().inflate (R.menu.review, menu);
		return true;
	}

	/**
	 * Need to hide/show the ignore button
	 */
	@Override
	public boolean onPrepareOptionsMenu (Menu menu) 
	{
		MenuItem mi;
		int i;
		
		for (i = 0; i < menu.size (); i++) {
			mi = menu.getItem (i);
			if (mi.getItemId () == R.id.em_ignore) {
				mi.setVisible (keyboard.canIgnore ());
			} else if (mi.getItemId () == R.id.em_fonts)
				mi.setVisible (keyboard.canOverrideFonts ());
		}
		
		return true;
	}
	
	/**
	 * Menu handler. Relays the call to the common {@link MenuHandler}.
	 * 	@param item the selected menu item
	 */
	@Override
	public boolean onOptionsItemSelected (MenuItem item)
	{
		return mh.onOptionsItemSelected (item) || super.onOptionsItemSelected (item);
	}

	protected void selectKeyboard ()
	{
		Keyboard oldk;
		
		oldk = keyboard;
		
		switch (kbstatus.getKeyboard (this)) {
		case LOCAL_IME:
			keyboard = localIMEKeyboard;
			break;
			
		case EMBEDDED:
			keyboard = embeddedKeyboard;
			break;
			
		case NATIVE:
			keyboard = nativeKeyboard;
			break;
		}
				
		if (keyboard != oldk && oldk != null)
			oldk.hide ();
		
		flushMenu ();
	}
	
	private void applyMuteSettings ()
	{
		boolean show;
		
		show = kbstatus.canMute () && SettingsActivity.getShowMute (this);
		keyboard.getMuteButton ().setVisibility (show ? View.VISIBLE : View.GONE);
		
		setMute (show && SettingsActivity.getMute (this));
	}
	
	private void setMute (boolean m)
	{
		AudioManager am;
		Drawable d;
		
		d = m ? muteDrawable : notMutedDrawable;
		keyboard.getMuteButton ().setImageDrawable (d);

		if (isMuted != m) {
			isMuted = m;
		
			am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
	    
			am.setStreamMute (AudioManager.STREAM_MUSIC, m);
		}
	}	
		
	/**
	 * Displays the splash screen, also providing a text message
	 * @param msg the text message to display
	 */
	protected void splashScreen (String msg)
	{
		msgw.setText (msg);
		contentView.setVisibility (View.GONE);
		splashView.setVisibility (View.VISIBLE);
	}
	
	/**
	 * Hides the keyboard
	 * @param kbstatus the new keyboard status
	 */
	protected void hide (KeyboardStatus kbstatus)
	{
		this.kbstatus = kbstatus;
		
		applyMuteSettings ();
		
		keyboard.hide ();
	}
	 
	protected void show (KeyboardStatus kbstatus)
	{
		this.kbstatus = kbstatus;

		selectKeyboard ();
		
		applyMuteSettings ();

		keyboard.show (kbstatus.hasEnter (this));
	}

	protected void iconize (KeyboardStatus kbs)
	{
		kbstatus = kbs;
		
		selectKeyboard ();
		
		applyMuteSettings ();
		
		keyboard.iconize (kbstatus.hasEnter (this));
	}
	
	protected void flushMenu ()
	{
		ActivityCompat.invalidateOptionsMenu (this);		
	}
	
	public void updateCanIgnore ()
	{
		flushMenu ();
	}
	
	protected void showIgnoreButtonMessage ()
	{
		AlertDialog.Builder builder;
		Dialog dialog;
					
		if (!visible || SettingsActivity.getIgnoreButtonMessage (this))
			return;
		
		builder = new AlertDialog.Builder (this);
		builder.setTitle (R.string.ignore_button_message_title);
		builder.setMessage (R.string.ignore_button_message_text);
		builder.setPositiveButton (R.string.ignore_button_message_ok, new OkListener ());
		
		dialog = builder.create ();
		
		dialog.show ();		
	}

}
