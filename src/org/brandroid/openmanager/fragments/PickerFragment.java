package org.brandroid.openmanager.fragments;

import org.brandroid.openmanager.R;
import org.brandroid.openmanager.activities.OpenExplorer;
import org.brandroid.openmanager.adapters.ContentAdapter;
import org.brandroid.openmanager.adapters.OpenPathPagerAdapter;
import org.brandroid.openmanager.data.OpenFile;
import org.brandroid.openmanager.data.OpenPath;
import org.brandroid.openmanager.views.OpenViewPager;
import org.brandroid.utils.Logger;
import org.brandroid.utils.MenuUtils;

import com.viewpagerindicator.PageIndicator;
import com.viewpagerindicator.TabPageIndicator;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;

public class PickerFragment extends OpenFragment
	implements OnItemClickListener, OpenPathFragmentInterface
{
	private Context mContext;
	private OnOpenPathPickedListener mPickListener;
	private View view;
	private ViewPager mPager;
	private FragmentPagerAdapter mPagerAdapter;
	private TextView mSelection;
	private EditText mPickName;
	private OpenPath mPath;
	private boolean pickDirOnly = true;
	private boolean mShowSelection = true;
	private String mDefaultName;
	
	public PickerFragment(Context context, OpenPath start)
	{
		mContext = context;
		mPath = start;
		Bundle args = getArguments();
		if(args == null)
			args = new Bundle();
		args.putParcelable("start", start);
		//onCreateView((LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE), null, null);		
	}
	
	public void setDefaultName(String name)
	{
		mDefaultName = name;
		if(mPickName != null)
			mPickName.setText(name);
		pickDirOnly = false;
	}
	
	public void setPickDirOnly(boolean pickDirOnly)
	{
		this.pickDirOnly = pickDirOnly;
	}
	public void setShowSelection(boolean showSelection)
	{
		mShowSelection = showSelection;
		if(mSelection != null)
			mSelection.setVisibility(View.GONE);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		view = inflater.inflate(R.layout.picker_pager, container, false);
		MenuUtils.setViewsVisible(view, false, android.R.id.button1, android.R.id.button2, android.R.id.title, R.id.pick_filename);
		mPager = (ViewPager)view.findViewById(R.id.picker_pager);
		mSelection = (TextView)view.findViewById(R.id.pick_path);
		
		//mGrid = (GridView)view.findViewById(android.R.id.list);
		//mGrid.setNumColumns(mContext.getResources().getInteger(R.integer.max_grid_columns));
		//mSelection = (TextView)view.findViewById(R.id.pick_path);
		//mPickName = (EditText)view.findViewById(R.id.pick_filename);
		return view;
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mPagerAdapter = new PickerPagerAdapter(getFragmentManager()).setContext(mContext);
		//mGrid.setOnItemClickListener(this);
		if(savedInstanceState != null && savedInstanceState.containsKey("start"))
			mPath = (OpenPath)savedInstanceState.getParcelable("start");
		setPath(mPath);
		if(!mShowSelection)
			MenuUtils.setViewsVisible(view, false, R.id.pick_path_row, R.id.pick_path);
		if(mPickName != null)
		{
			if(pickDirOnly)
				mPickName.setVisibility(View.GONE);
			else if(savedInstanceState != null && savedInstanceState.containsKey("name"))
				mPickName.setText(savedInstanceState.getString("name"));
			else mPickName.setText(mDefaultName);
		}
	}
	
	public void setPath(OpenPath path)
	{
		mPath = path;
		((PickerPagerAdapter)mPagerAdapter)
			.setOnItemClickListener(this)
			.setPath(path);
		mPager.setCurrentItem(path.getDepth() - 1);
		/*
		mAdapter = new ContentAdapter(mContext, OpenExplorer.VIEW_LIST, mPath);
		mAdapter.setShowPlusParent(path.getParent() != null);
		mAdapter.setShowFiles(false);
		mAdapter.setShowDetails(false);
		mAdapter.updateData();
		mGrid.setAdapter(mAdapter);
		*/
		if(mSelection != null && mShowSelection)
			mSelection.setText(mPath.getPath());
		if(mPager != null && mPagerAdapter != null && mPager.getAdapter() == null)
			try {
				mPager.setAdapter(mPagerAdapter);
			} catch(IllegalStateException e) { Logger.LogError("Illegal State in PickerFragment?", e); }
	}
	
	public interface OnOpenPathPickedListener
	{
		public void onOpenPathPicked(OpenPath path);
	}
	
	public void setOnOpenPathPickedListener(OnOpenPathPickedListener listener)
	{
		mPickListener = listener;
	}
	
	@Override
	public void onItemClick(AdapterView<?> list, View view, int pos, long id) {
		OpenPath path = ((ContentAdapter)list.getAdapter()).getItem(pos);
		if(mPickListener != null)
			mPickListener.onOpenPathPicked(path);
		else
			setPath(path);
	}
	
	@Override
	public View getView() {
		return view;
	}
	
	@Override
	public Drawable getIcon() {
		return null;
	}

	@Override
	public CharSequence getTitle() {
		return getPath().getPath();
	}

	@Override
	public OpenPath getPath() {
		OpenPath ret = mPath;
		if(mPickName != null && mPickName.getVisibility() == View.VISIBLE && mPickName.getText() != null)
			ret = ret.getChild(mPickName.getText().toString());
		return ret;
	}
	
	static class PickerPagerAdapter extends FragmentPagerAdapter
	{
		private OpenPath mPath;
		private OnItemClickListener mListener;
		private Context mContext;

		public PickerPagerAdapter(FragmentManager fm) {
			super(fm);
		}
		
		public PickerPagerAdapter setContext(Context c) { mContext = c; return this; }
		
		public PickerPagerAdapter setPath(OpenPath path)
		{
			mPath = path;
			notifyDataSetChanged();
			return this;
		}
		private PickerPagerAdapter setOnItemClickListener(OnItemClickListener l)
		{
			mListener = l;
			return this;
		}

		@Override
		public Fragment getItem(int position) {
			OpenPath path = mPath.getAncestors(true).get(getCount() - position - 1);
			SimpleContentFragment ret = new SimpleContentFragment(mContext, path);
			ret.setOnItemClickListener(mListener);
			return ret;
		}

		@Override
		public int getCount() {
			return mPath.getDepth();
		}
		
	}

}