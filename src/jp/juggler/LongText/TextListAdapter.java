package jp.juggler.LongText;

import jp.juggler.util.LogCategory;
import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
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
		
	@Override
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

	static class ViewHolder{
		TextView text;
		BulkText.Line line;
	}
	
	public View getView(int pos, View view , ViewGroup parent){
		ViewHolder holder;
		if(view==null){
			view = inflater.inflate(layout_id, null);
			holder = new ViewHolder();
			view.setTag(holder);
			holder.text = (TextView)view.findViewById(R.id.text);
			holder.line = new BulkText.Line();
		}else{
			holder = (ViewHolder)view.getTag();
		}
		cache.loadLine(holder.line,pos);
		holder.text.setText(holder.line);
		holder.text.setBackgroundColor( ((ListView)parent).isItemChecked(pos)? 0x88888888: 0x00000000 );
		return view;
	}

	void clear(){
		row_count = 0;
		notifyDataSetChanged();
	}
	void setLineCount(int lines){
		row_count = lines;
		notifyDataSetChanged();
	}
	
	//////////////////////////////////////
	// belows are same as BaseAdapter
	
    private final DataSetObservable mDataSetObservable = new DataSetObservable();

    @Override
	public boolean hasStableIds() {
        return false;
    }
    
    @Override
	public void registerDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.registerObserver(observer);
    }

    @Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.unregisterObserver(observer);
    }
    
    @Override
	public void notifyDataSetChanged() {
        mDataSetObservable.notifyChanged();
    }
    
    @Override
	public void notifyDataSetInvalidated() {
        mDataSetObservable.notifyInvalidated();
    }

    @Override
	public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
	public boolean isEnabled(int position) {
        return true;
    }

    @Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent);
    }

    @Override
	public int getItemViewType(int position) {
        return 0;
    }

    @Override
	public int getViewTypeCount() {
        return 1;
    }
}
