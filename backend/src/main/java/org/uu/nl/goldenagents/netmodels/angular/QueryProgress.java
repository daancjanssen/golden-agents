package org.uu.nl.goldenagents.netmodels.angular;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.uu.nl.goldenagents.agent.context.query.QueryProgressType;

public class QueryProgress<T> {
	
	private String queryID;
	private int index;
	private String type;
	private T value;
	private boolean finished;
	private List<QueryProgressSubResult> subresults;
	
	public QueryProgress(String queryID, QueryProgressType type, boolean finished) {
		super();
		this.queryID = queryID;
		this.index = type.toIndex();
		this.type = type.name();
		this.finished = finished;
	}

	public QueryProgress(String queryID, QueryProgressType type, T value, boolean finished) {
		super();
		this.queryID = queryID;
		this.index = type.toIndex();
		this.type = type.name();
		this.value = value;
		this.finished = finished;
	}
	
	public String getQueryID() {
		return queryID;
	}

	public void setQueryID(String queryID) {
		this.queryID = queryID;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public T getValue() {
		return value;
	}
	public void setValue(T value) {
		this.value = value;
	}
	public boolean getFinished() {return finished; }
	public void setFinished(boolean finished) {this.finished = finished;}
	public List<QueryProgressSubResult> getSubresults() {return subresults; }

	private void ensureSubsExists() {
		if(this.subresults == null) {
			this.subresults = new ArrayList<>();
		}
	}

	public void addSubresult(QueryProgressSubResult... subResults) {
		ensureSubsExists();
		this.subresults.addAll(Arrays.asList(subResults));
	}

	public void addSubresult(QueryProgressSubResult subResult) {
		ensureSubsExists();
		this.subresults.add(subResult);
	}

	@Override
	public String toString() {
		return String.format("Query progress [queryID=%s index=%d type=%s value=%s done=%b]",
				queryID, index, type, value == null ? "" : value.toString(), finished);
	}

	public static class QueryProgressSubResult {
		private String stringValue;
		private long longValue;
		private boolean finished;

		public QueryProgressSubResult(String stringValue, boolean finished) {
			this.stringValue = stringValue;
			this.finished = finished;
		}

		public QueryProgressSubResult(String stringValue, long longValue, boolean finished) {
			this.stringValue = stringValue;
			this.longValue = longValue;
			this.finished = finished;
		}

		public String getStringValue() {
			return stringValue;
		}

		public void setStringValue(String stringValue) {
			this.stringValue = stringValue;
		}

		public long getLongValue() {
			return longValue;
		}

		public void setLongValue(long longValue) {
			this.longValue = longValue;
		}

		public boolean isFinished() {
			return finished;
		}

		public void setFinished(boolean finished) {
			this.finished = finished;
		}
	}
	
}