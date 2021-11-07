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
			run();
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
			for(int i = 0; i<manifest.tracks().get(0).segments().size();i++){
				Track t0= manifest.tracks().get(i);
				Segment s0 = t0.segments().get(i);
				System.err.println(s0.offset());
				System.err.println(s0.length());
				SegmentContent sg = new SegmentContent(t0.contentType(), http.doGetRange(String.format("%s/%s/%s",MEDIA_SERVER_BASE_URL ,this.movie, t0.filename()),s0.offset(),s0.length()));
				queue.add(sg);
			}
			
		}
	}
}
