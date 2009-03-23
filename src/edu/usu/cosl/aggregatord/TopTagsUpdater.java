package edu.usu.cosl.aggregatord;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TopTagsUpdater extends DBThread 
{
	private static final double DEFAULT_POLL_INTERVAL = 5; 
	private static double dPollInterval = DEFAULT_POLL_INTERVAL;

	private void updateTopTagsForAggregation(Statement st, int nAggregationID)
	{
		try
		{
			// nuke the existing top tags
			String sql = "DELETE FROM aggregation_top_tags WHERE aggregation_id = " + nAggregationID;
			st.executeUpdate(sql);
			
			// create the new top 600 for the aggregation
			sql = "INSERT INTO aggregation_top_tags (aggregation_id, tag_id, hits) (" +
					"SELECT " + nAggregationID + ", id, hits FROM (" + 
					"SELECT tags.id, COUNT(entries_tags.entry_id) as hits " + 
					"FROM entries_tags " +
					"INNER JOIN tags ON entries_tags.tag_id = tags.id " +
					"INNER JOIN entries ON entries_tags.entry_id = entries.id " +
					"INNER JOIN feeds ON entries.feed_id = feeds.id " +
					"INNER JOIN aggregation_feeds ON aggregation_feeds.feed_id = feeds.id " +
					"WHERE aggregation_feeds.aggregation_id = " + nAggregationID + " " + 
					"GROUP BY tags.id " + 
					"ORDER BY hits DESC LIMIT 600) AS t) ";
			st.executeUpdate(sql);
		}
		catch (Exception e)
		{
			Logger.error(e);
		}
	}
	
	private void updateStaleAggregations() throws SQLException, ClassNotFoundException
	{
		boolean bUpdatedFeeds = false;

		Connection cn = getConnection();
		Statement st = cn.createStatement();
		
		// see if any feeds have been updated
		ResultSet rs = st.executeQuery("SELECT id FROM entries WHERE (AGE(now(), created_at) < '" + 2*DEFAULT_POLL_INTERVAL + " seconds') LIMIT 1");
		if (rs.next()) bUpdatedFeeds = true;
		rs.close();
		
		// some feed is updated so we need to update the tags for all of the aggregations 
		if (bUpdatedFeeds)
		{
			// get the list of aggregations
			rs = st.executeQuery("SELECT id FROM aggregations");
			Statement ast = cn.createStatement();
			while (rs.next())
			{
				updateTopTagsForAggregation(ast, rs.getInt(1));
			}
			ast.close();
			rs.close();
		}
		st.close();
		cn.close();
		if (bUpdatedFeeds) Logger.info("Updated statistics");
	}

	public void run() 
	{
		try 
		{
			while(!bStop)
			{
				// stale aggregations
				updateStaleAggregations();
				
				// sleep until its time to harvest again
				if (!bStop) Thread.sleep((long)(dPollInterval*1000));
			}
		}
		catch (Exception e) 
		{
			Logger.error("Error in stats updater: " + e);
		}
	}
}
