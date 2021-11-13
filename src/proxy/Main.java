package proxy;

import java.util.concurrent.BlockingQueue;

import http.HttpClient;
import http.HttpClient10;
import media.MovieManifest;
import media.MovieManifest.Manifest;
import media.MovieManifest.Segment;
import media.MovieManifest.SegmentContent;
import media.MovieManifest.Track;
import proxy.server.ProxyServer;

import java.nio.charset.StandardCharsets;

public class Main {
	static final String MEDIA_SERVER_BASE_URL = "http://localhost:9999";

	public static void main(String[] args) throws Exception {

		ProxyServer.start( (movie, queue) -> new DashPlaybackHandler(movie, queue) );
		
	}
	/**
	 * TODO TODO TODO TODO
	 * 
	 * Class that implements the client-side logic.
	 * 
	 * Feeds the player queue with movie segment data fetched
	 * from the HTTP server.
	 * 
	 * The fetch algorithm should prioritize:
	 * 1) avoid stalling the browser player by allowing the queue to go empty
	 * 2) if network conditions allow, retrieve segments from higher quality tracks
	 */
	static class DashPlaybackHandler implements Runnable  {
		
		final String movie;
		final Manifest manifest;
		final BlockingQueue<SegmentContent> queue;

		final HttpClient http;
		
		DashPlaybackHandler( String movie, BlockingQueue<SegmentContent> queue) {
			this.movie = movie;
			this.queue = queue;
			
			this.http = new HttpClient10();
			
			String manifestPath = String.format("%s/%s/manifest.txt", MEDIA_SERVER_BASE_URL, this.movie);
			String manifestText= new String(http.doGet(manifestPath),StandardCharsets.UTF_8);
			this.manifest = MovieManifest.parse(manifestText);

		}
		
		/**
		 * Runs automatically in a dedicated thread...
		 * 
		 * Needs to feed the queue with segment data fast enough to
		 * avoid stalling the browser player
		 * 
		 * Upon reaching the end of stream, the queue should
		 * be fed with a zero-length data segment
		 */
		public void run() {
			SegmentContent sg;
			Track t=manifest.tracks().get(0);
			Segment s;
			long startTime;
			double timeTaken;
			double bandwidth=0;
			for(int i = 0; i<t.segments().size();i++){
				t= chooseTrack(bandwidth,t);
				s = t.segments().get(i);
				startTime= System.nanoTime();
				sg = new SegmentContent(t.contentType(), http.doGetRange(String.format("%s/%s/%s",MEDIA_SERVER_BASE_URL ,this.movie, t.filename()),s.offset(),s.offset()+s.length()-1));
				timeTaken= (double)(System.nanoTime()-startTime)/1000000000;
				bandwidth= s.length()*8/timeTaken;
				try {queue.put(sg);
				}
				catch (Exception x){
					x.printStackTrace();
				}
			}
			while(true){
				byte[] b= new byte[0];
				sg = new SegmentContent(t.contentType(), b);
				try {queue.put(sg);
				}
				catch (Exception x){
					x.printStackTrace();
				}
			}
		}
		private Track chooseTrack(double bandwidth, Track t){
			Track tmp=null;
			for(int j = manifest.tracks().size()-1;j>=0;j--){
				tmp=manifest.tracks().get(j);
				if(bandwidth>=tmp.avgBandwidth()+10000){
					if(t.equals(tmp)){	
						return tmp;
					}
					Segment s = tmp.segments().get(0);
					SegmentContent sg = new SegmentContent(tmp.contentType(), http.doGetRange(String.format("%s/%s/%s",MEDIA_SERVER_BASE_URL ,this.movie, tmp.filename()),s.offset(),s.offset()+s.length()-1));
					try{ queue.put(sg);
					return tmp;
					}
					catch (Exception x){
						x.printStackTrace();
					}
				}
			}
			return tmp;
		}
	}
}
