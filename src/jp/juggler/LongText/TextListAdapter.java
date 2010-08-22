package jp.juggler.LongText;

import jp.juggler.util.LogCategory;
import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class TextListAdapter extends BaseAdapter {
	static LogCategory log = new LogCategory("TextListAdapter");
	LayoutInflater inflater;
	static final int layout_id = R.layout.text_item;
	Object dummy_item = new String("this is dummy");	
	int row_count = 0;
	TextCache cache;

	public TextListAdapter(Context context,TextCache cache){
		super();
		this.cache = cache;
		this.inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}	
	
	/////////////////////////////////////////////////////////
		
	public boolean isEmpty() {
        return row_count == 0;
    }

	public int getCount() {
		return row_count;
	}

	public Object getItem(int pos) {
		return null;
	}

	public long getItemId(int pos) {
		return (long) pos +1;
	}

	public View getView(int pos, View view , ViewGroup parent){
		if(view==null) view = inflater.inflate(layout_id, null);
		TextView tvText = (TextView)view.findViewById(R.id.text);
		tvText.setText(cache.getLine(pos));
		return view;
	}

	void clear(){
		row_count = 0;
		notifyDataSetChanged();
	}
	void addLineCount(int lines){
		row_count += lines;
		notifyDataSetChanged();
	}
	
	//////////////////////////////////////
	// belows are same as BaseAdapter
	
    private final DataSetObservable mDataSetObservable = new DataSetObservable();

    public boolean hasStableIds() {
        return false;
    }
    
    public void registerDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.registerObserver(observer);
    }

    public void unregisterDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.unregisterObserver(observer);
    }
    
    public void notifyDataSetChanged() {
        mDataSetObservable.notifyChanged();
    }
    
    public void notifyDataSetInvalidated() {
        mDataSetObservable.notifyInvalidated();
    }

    public boolean areAllItemsEnabled() {
        return false;
    }

    public boolean isEnabled(int position) {
        return false;
    }

    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent);
    }

    public int getItemViewType(int position) {
        return 0;
    }

    public int getViewTypeCount() {
        return 1;
    }
}
