package com.example.popularmovies;

import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.example.popularmovies.db.MoviesContract;
import com.example.popularmovies.db.SavedMovieInfo;
import com.example.popularmovies.themoviedb.Api3;
import com.example.popularmovies.themoviedb.TmdbMoviesPage;
import com.example.popularmovies.utils.Options;

import static android.support.v7.widget.RecyclerView.NO_POSITION;


public class MainActivity extends AppCompatActivity
  implements Response.ErrorListener, Response.Listener<TmdbMoviesPage>, MoviesListAdapter.OnClickListener, LoaderManager.LoaderCallbacks<Cursor> {
  
  private static final String TAG = Options.XTAG + MainActivity.class.getSimpleName();
  
  // Saved instance state keys.
  private static final String SAVED_KEY_POSITION = "pos";
  
  private static final int MOVIES_LOADER_ID = 1;
  
  private Api3 api3;
  
  private RecyclerView mRecyclerView;
  private SwipeRefreshLayout mSwipeRL;
  
  private int savedPosition = NO_POSITION;  // Saved RecyclerView's position.
  private final int mCurrentPage = 1;       // In future could be increased in some way.
  
  private Request<TmdbMoviesPage> mPageRequest = null;
  private MoviesListAdapter mAdapter;
  
  
  @Override
  protected void onCreate (final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_with_recyclerview);
    
    // Detect screen orientation to decide on columns count.
    boolean isLandscape = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
    
    // Find views.
    mRecyclerView = findViewById(R.id.main_recyclerview);
    mSwipeRL = findViewById(R.id.main_swipe_layout);
    
    // Setup RecyclerView.
    GridLayoutManager layman = new GridLayoutManager(this, isLandscape ? 4 : 2);
    mRecyclerView.setLayoutManager(layman);
    mAdapter = new MoviesListAdapter(this, this);
    mRecyclerView.setAdapter(mAdapter);
    mRecyclerView.setHasFixedSize(true); // All posters assumed to be same size.
    if (savedInstanceState != null) savedPosition = savedInstanceState.getInt(SAVED_KEY_POSITION, NO_POSITION);
    
    // Change activity title.
    Options.CurrentTab currentTab = Options.getInstance(this).getCurrentTab();
    setDynamicTitle(currentTab);
    
    // Setup "swipe to refresh".
    mSwipeRL.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {@Override public void onRefresh () {
      requireMovies();  
    }});
    
    // Begin network request.
    api3 = new Api3(BuildConfig.THEMOVIEDB_API_KEY, this);
    requireMovies();
    
    // For non-favorites tab start loader anyway to get favorites list.
    if (Options.getInstance(MainActivity.this).getCurrentTab() != Options.CurrentTab.FAVORITES) {
      getLoaderManager().initLoader(MOVIES_LOADER_ID, null, this);
    }
  }
  
  
  private int getRecyclerViewPosition() {
    GridLayoutManager lm = (GridLayoutManager)mRecyclerView.getLayoutManager();
    int pos = lm.findFirstCompletelyVisibleItemPosition();
    if (pos == NO_POSITION) pos = lm.findFirstVisibleItemPosition(); // If all items partially invisible.
    return pos;
  }
  
  
  @Override
  protected void onSaveInstanceState (Bundle outState) {
    super.onSaveInstanceState(outState);
    if (savedPosition != NO_POSITION) {
      savedPosition = getRecyclerViewPosition();
      outState.putInt(SAVED_KEY_POSITION, savedPosition);
    }
  }
  
  
  @Override
  protected void onStop () {
    super.onStop();
    if (mPageRequest != null) mPageRequest.cancel(); // Stop fetching movies list from the network.
  }
  
  
  private void requireMovies() {
    mRecyclerView.setVisibility(View.INVISIBLE);
    mAdapter.setMovies(null, Options.getInstance(this).isFavoriteTab());
    switch (Options.getInstance(MainActivity.this).getCurrentTab()) {
      case FAVORITES:
        getLoaderManager().restartLoader(MOVIES_LOADER_ID, null, this);
        break;
      case POPULAR:
        mPageRequest = api3.requirePopularMovies(mCurrentPage, this, this);
        break;
      case TOP_RATED:
        mPageRequest =  api3.requireTopRatedMovies(mCurrentPage, this, this);
        break;
    }
    mSwipeRL.setRefreshing(true);
  }
  
  
  @Override
  public boolean onCreateOptionsMenu (final Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }
  
  
  /**
   * Set activity title based on sort order: Popular or Top Rated movies.
   */
  private void setDynamicTitle (Options.CurrentTab currentTab) {
    setTitle(currentTab.toTranslatableString(this));
  }
  
  
  /**
   * Switch main activity to ths specified tab (movies list criteria).
   * @param tab Required tab.
   */
  private void switchTab (Options.CurrentTab tab) {
    savedPosition = 0; // Force reset position to zero.
    Options.getInstance(this).setCurrentTab(tab);
    setDynamicTitle(tab);
    requireMovies();
  }
  
  
  @Override
  public boolean onOptionsItemSelected (final MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_about:
        startActivity(new Intent(this, AboutActivity.class));
        return true;
      case R.id.menu_favorites:
        switchTab(Options.CurrentTab.FAVORITES);
        return true;
      case R.id.menu_popular:
        switchTab(Options.CurrentTab.POPULAR);
        return true;
      case R.id.menu_top_rated:
        switchTab(Options.CurrentTab.TOP_RATED);
        return true;
    }
    return super.onContextItemSelected(item);
  }
  
  
  /**
   * Called if some error occurred while executing network request.
   * @param error
   */
  @Override
  public void onErrorResponse (final VolleyError error) {
    mSwipeRL.setRefreshing(false);
    Log.w(TAG, "onErrorResponse(): " + error.getMessage());
    
    Snackbar.make(mRecyclerView, R.string.fail_get_movies_list, Snackbar.LENGTH_LONG)
      .setAction(R.string.refresh_big, new View.OnClickListener() { @Override public void onClick (View v) {
        requireMovies();
      }})
      .show();
  }
  
  
  /**
   * This code called both from network and database request completion routines to prepare
   * activity to display received data.
   */
  private void finishDataLoading() {
    mSwipeRL.setRefreshing(false);
    mRecyclerView.setVisibility(View.VISIBLE);
    if (savedPosition == NO_POSITION) savedPosition = 0;
    mRecyclerView.scrollToPosition(savedPosition);
  }
  
  
  /**
   * Called if TMDb network request succeeds.
   * @param response 
   */
  @Override
  public void onResponse (final TmdbMoviesPage response) {
    mAdapter.setMovies(SavedMovieInfo.createArray(response.results), Options.getInstance(this).isFavoriteTab());
    finishDataLoading();
  }
  
  
  /**
   * Called when RecyclerView item was clicked.
   * @param item Clicked item number.
   */
  @Override
  public void onClickItem (int item) {
    Intent intent = new Intent(this, DetailsActivity.class);
    intent.putExtra(DetailsActivity.EXTRA_MOVIE, mAdapter.getMovie(item));
    intent.putExtra(DetailsActivity.EXTRA_IS_FAVORITE, mAdapter.isFavorite(item));
    startActivity(intent);
  }
  
  
  /**
   * Called when RecyclerView item's star (favorites mark) was clicked.
   * @param item Clicked item number.
   */
  @Override
  public void onClickStar (int item) {
    savedPosition = getRecyclerViewPosition();
    mAdapter.switchFavorite(item);
  }
  
  
  @Override
  public Loader<Cursor> onCreateLoader (int id, Bundle args) {
    if (id != MOVIES_LOADER_ID) throw new RuntimeException("Loader Not Implemented: " + id);
    return new CursorLoader(this, MoviesContract.FavoriteMovies.CONTENT_URI, null, null, null, null);
  }
  
  
  @Override
  public void onLoadFinished (Loader<Cursor> loader, Cursor cursor) {
    mAdapter.swapCursor(cursor);
    finishDataLoading();
  }
  
  
  @Override
  public void onLoaderReset (Loader<Cursor> loader) {
    mAdapter.swapCursor(null);
  }
}
