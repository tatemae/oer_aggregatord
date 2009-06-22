package edu.usu.cosl.aggregatord;

import java.util.Vector;
import java.util.Enumeration;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.concurrent.ArrayBlockingQueue;

import edu.usu.cosl.util.Logger;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;

/*
 * TODO: Write a plugin architecture for data sources
 * TODO: See if we can get ROME and other data sources do e-tag and gzip options
 * TODO: Add db logging to the Logger class
 */

/*
 * Things to log:
 * 	URL failure request
 *  Invalid feed
 *  Unexpected shutdown
 *  Harvest stats
 *  Harvest takes longer than wait interval
 */
public class AggregatorDaemon implements Daemon
{
	// worker threads
	private static ArrayBlockingQueue<FeedInfo> toDoQueue;
	private static ArrayBlockingQueue<String> activeJobsQueue;
	private static Harvester[] workerThreads;

	// number of threads to use (we reserve one for the main process) 
	private static final int DEFAULT_THREADS = 2;
	private static int nWorkerThreads = DEFAULT_THREADS - 1;

	// size of the stale feed queue to use 
	private static final int DEFAULT_STALE_FEED_QUEUE_SIZE = DEFAULT_THREADS;
	private static int nStaleFeedQueueSize = DEFAULT_STALE_FEED_QUEUE_SIZE*5;

	// seconds to wait before polling the database for stale feeds
	private static final double DEFAULT_POLL_INTERVAL = 5; 
	private static double dPollInterval = DEFAULT_POLL_INTERVAL;
	
	public static void main(String[] args) 
	{
		Logger.getLogger();
		AggregatorDaemon daemon = new AggregatorDaemon();
		daemon.init(null);
		daemon.start();
	}

	private void startWorkerThreads(int nWorkerThreads)
	{
		toDoQueue = new ArrayBlockingQueue<FeedInfo>(nStaleFeedQueueSize);
		activeJobsQueue = new ArrayBlockingQueue<String>(nStaleFeedQueueSize);
		
		workerThreads = new Harvester[nWorkerThreads];
		for (int nThread = 0; nThread < nWorkerThreads; nThread++) 
		{
			workerThreads[nThread] = new Harvester(toDoQueue, activeJobsQueue);
			workerThreads[nThread].start();
		}
		Logger.info("Started " + nWorkerThreads + " worker threads");
	}
	
	private void stopWorkerThreads() throws InterruptedException
	{
		// add special end-of-stream markers to terminate the workers
		for (int nThread = 0; nThread < workerThreads.length; nThread++) 
		{
			toDoQueue.put(FeedInfo.NO_MORE_WORK);
		}
	}
	
	private void harvestFeeds(Vector<FeedInfo> vFeeds)
	{
		try 
		{
			// add some work to the queue; block if the queue is full
			// note that null cannot be added to a blocking queue
			for (Enumeration<FeedInfo> eFeeds = vFeeds.elements(); eFeeds.hasMoreElements();) 
			{
				FeedInfo fi = eFeeds.nextElement();
				
				// only put the item in the queue if it is not already there
				if (!(activeJobsQueue.contains(fi.sURI) || toDoQueue.contains(fi)))
				{
//					System.out.println("adding to queue: " + fi.sTitle);
					activeJobsQueue.put(fi.sURI);
					toDoQueue.put(fi);
				}
//				else System.out.println("already in queue: " + fi.sURI);
			}
//			System.out.println("feeds in queue: " + toDoQueue.size());
		}
		// a worker thread got interrupted unexpectedly
		catch (InterruptedException e) 
		{
			System.out.println(e.toString());
		}
	}

	private void getConfigOptions()
	{
		Properties properties = new Properties();
	    try 
	    {
	    	// load the property file
	    	FileInputStream in = new FileInputStream("aggregatord.properties");
	        properties.load(in);
	        in.close();
	        
	        // # of worker threads
	        String sValue = properties.getProperty("threads");
	        if (sValue != null)
	        {
	        	nWorkerThreads = Integer.parseInt(sValue);
	        	nStaleFeedQueueSize = nWorkerThreads*5; 
	        }
	        // stale feed queue size
	        sValue = properties.getProperty("stale_feed_queue_size");
	        if (sValue != null) nStaleFeedQueueSize = Integer.parseInt(sValue);
	        
	        // interval in seconds to wait between querying the database for stale feeds 
	        sValue = properties.getProperty("stale_thread_poll_interval");
	        if (sValue != null) dPollInterval = Double.parseDouble(sValue);

	        // seconds to wait before timing out http requests
	        sValue = properties.getProperty("request_timeout");
	        if (sValue != null) Harvester.setConnectionTimeout(Integer.parseInt(sValue));
	        
	        Logger.getOptions(properties);
	    }
	    catch (IOException e) 
	    {
	    	System.out.println("error reading aggregatord.properties file");
	    }
	}
	public void destroy()
	{
		
	}
	public void init(DaemonContext context)
	{
		// get configuration options
		getConfigOptions();
		
		// URLConnection's timeout mechanism doesn't work so we have to set this property
		System.setProperty("sun.net.client.defaultReadTimeout","" + Harvester.getConnectionTimeout()*1000);
		
		// create a thread pool for harvesting feeds
		startWorkerThreads(nWorkerThreads);
		
	}
	public void start()
	{
		try 
		{
			boolean bRun = true;
			while(bRun)
			{
				// get a list of stale feeds
				Vector<FeedInfo> vStaleFeeds = Harvester.getStaleFeeds();
				
				// report how many available worker threads we have
				logThreadsStatus(vStaleFeeds.size());
				
				// null means something bad happened
				if (vStaleFeeds == null) break;

				// non-empty vector means we have feeds to harvest
				if (vStaleFeeds.size() > 0)
				{
					// harvest the feeds
					harvestFeeds(vStaleFeeds);
				}
				// sleep until its time to harvest again
				Thread.sleep((long)(dPollInterval*1000));
			}
		}
		catch (InterruptedException e)
		{
			System.out.println(e);
		}
	}
	private void logThreadsStatus(int nStaleFeeds)
	{
		int nRunningThreads = 0;
		for (int nThread = 0; nThread < workerThreads.length; nThread++)
		{
			if (Thread.State.RUNNABLE == workerThreads[nThread].getState()) nRunningThreads++;
		}
		Logger.status("Status (stale, runnable, active, to do): " + nStaleFeeds + ", " + nRunningThreads + ", " + activeJobsQueue.size() + ", " + toDoQueue.size());
	}
	public void stop()
	{
		try 
		{
			// let the worker threads know they can exit
			stopWorkerThreads();
		}
		catch (InterruptedException e)
		{
			System.out.println(e);
		}
	}
}
