/*                                                                              
/ CS6333 Computer Networks -- Project 3: HTTP Server                            
/ Created by: Matt Weeden                                                       
/ Dec 16, 2015                                                                  
/                                                                               
/ This program implements a simple HTTP/1.1 server that can respond             
/ to GET and HEAD requests from a client.                                       
/                                                                               
/ cmd line INPUT: (int) server port number                                      
 */

import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class HttpServer {

	// thread for handling incoming messages                                                                          
	public class HandleRequest implements Runnable {
		Socket cs;
		ServerSocket ss;
		BufferedReader in;
		PrintWriter out;

		String requestMethod = "";
		int statusCode = 200;

		String filename = "";
		String fileType;
		int fileLength = 0;
		byte[] fileContentsBytes;
		String fileContentsString;

		String temp;
		ArrayList<String> msg = new ArrayList<String>();

		boolean hostHeaderFound = false;
		String requestedHostName = "";

		String responseHeader = "";
		String responseBody = "";

		public HandleRequest(Socket client, ServerSocket server) {
			cs = client;
			ss = server;
		}

		public void run() {
			System.out.println("\n=============================================");
			
			/////////////////////////////////////////////////////////////////////
			//        establish streams and read the client's request	 	   //
			/////////////////////////////////////////////////////////////////////

			// declare these objects outside the try block scope
			BufferedReader in=null;
			PrintWriter out=null;
			FileInputStream fis=null;

			try{	
				// establish the in and out streams to the client
				in = new BufferedReader(
						new InputStreamReader(cs.getInputStream()));
				out = new PrintWriter(cs.getOutputStream());

				String line;
				while ((line = in.readLine()) != null) {
					if (line.length() == 0)
						break;
					msg.add(line);
				}

			} catch (Exception e) {
				System.err.println("== Unable to read the client's message ==\n\n");
				e.printStackTrace();
			}

			// add a blank entry if no message is received
			if (msg.size() == 0) {
				msg.add("");
			}
			
			System.out.println("Client Request Header:\n");
			
			// print out what is received from the client
			for (int i=0; i<msg.size(); i++) {
				System.out.println("   " + msg.get(i));
			}

			/////////////////////////////////////////////////////////////////////
			//                interpret the client's request  	 			   //
			/////////////////////////////////////////////////////////////////////

			if (msg.get(0).startsWith("GET ")) {
				requestMethod = "get";
				filename = msg.get(0).substring(5, msg.get(0).length()-9);
				filename = filename.trim();
			} else if (msg.get(0).startsWith("HEAD ")) {
				requestMethod = "head";
				filename = msg.get(0).substring(5, msg.get(0).length()-9);
				filename = filename.trim();
			} else {
				System.err.println("\n== The request method is not supported " +
						"by this server ==");
				statusCode = 501;
			}

			for (int i=0; i<msg.size(); i++) {
				if (msg.get(i).startsWith("Host")) {
					requestedHostName = msg.get(i).substring(6, msg.get(i).indexOf(":", 6));
					hostHeaderFound = true;
				}
			}

			// catch if there was no host header
			if (!hostHeaderFound) {
				System.err.println("\n== No host header was found in the client's" +
						"request ==\n");
				statusCode = 400;
			}

			/////////////////////////////////////////////////////////////////////
			//                  read from the requested file 			 	   //
			/////////////////////////////////////////////////////////////////////

			if (statusCode == 200) {
				// catch if the Request-URI is empty
				if (filename.length() <= 0) {
					System.err.println("No requested file name was given.");
					statusCode = 400;
					// catch if the request method is not a valid method
				} else {
					try{
						File f = new File("public_html/" + filename);
						//File f = new File("src/public_html/" + filename);
						fis = new FileInputStream(f);
						fileType = URLConnection.guessContentTypeFromName(filename);

						////////////////////////////////////////////////////////////////////////
						// construct the response body (bytes for images and String for html) //
						////////////////////////////////////////////////////////////////////////

						fileContentsBytes = new byte[fis.available()];
						fis.read(fileContentsBytes);
					} catch (IOException e1) {
						System.err.println("\n== Unable to access the requested file ==");
						statusCode = 404;
					}
				}
				
				if (statusCode == 200) {
					// convert the file's contents from bytes to a String
					if (filename.endsWith("html")) {
						fileContentsString = String.valueOf((char)fileContentsBytes[0]);
						for (int i=1; i<fileContentsBytes.length; i++) {
							fileContentsString += String.valueOf((char)fileContentsBytes[i]);
						}
					}
				}
			}

			/////////////////////////////////////////////////////////////////////
			//                  construct the response header 			 	   //
			/////////////////////////////////////////////////////////////////////

			// add the StatusCode and ReasonPhrase
			if (statusCode == 200) {
				responseHeader += "HTTP/1.1 " + statusCode + " ok\r\n";
			}
			else if (statusCode == 400) {
				responseHeader += "HTTP/1.1 " + statusCode + " Bad Request\r\n";
			}
			else if (statusCode == 404) {
				responseHeader += "HTTP/1.1 " + statusCode + " Not Found\r\n";
			}
			else if (statusCode == 501) {
				responseHeader += "HTTP/1.1 " + statusCode + " Not Implemented\r\n";
			} else {
				responseHeader += "HTTP/1.1 " + statusCode + " Unknown Status Code\r\n";
			}

			// add the Server field
			responseHeader += "Server: mattWeedenHTTP/1.0\r\n";

			// add the Content-length field
			if (statusCode == 200) {
				if (filename.endsWith("html")) {
					responseHeader += "Content-Length: " + fileContentsString.length() + "\r\n";
				} else {
					responseHeader += "Content-Length: " + fileContentsBytes.length + "\r\n";
				}
			} else {
				responseHeader += "Content-Length: 0\r\n";
			}

			// add the Content-Type field
			if (statusCode == 200) {
				responseHeader += "Content-Type: " + fileType + "\r\n";
			} else {
				responseHeader += "Content-Type: null\r\n";
			}

			// end the response header
			responseHeader += "\r\n";

			/////////////////////////////////////////////////////////////////////
			//                       send the response 			 	   		   //
			/////////////////////////////////////////////////////////////////////

			// send response header
			out.print(responseHeader);

			// print out the head to the server's console
			System.out.println("\nResponse Header:\n");
			for (String line : responseHeader.split("\r\n")) {
				System.out.println("   " + line);
			}

			// send response body if required
			if (statusCode == 200 && requestMethod == "get") {
				if (filename.endsWith("html")) {
					out.println(fileContentsString);
				} else {
					try {
						for (byte b : fileContentsBytes) {
							cs.getOutputStream().write(b);
						}
					} catch (IOException e) {
						System.err.println("\n== Unable to write the image file bytes" +
								"to the output stream ==");
					}

				}
			}

			// close the sockets and the input, output, and file streams
			out.close();
			try {
				in.close();
				cs.close();     
				ss.close();
				if (statusCode == 200 && filename.endsWith("html")) {
					fis.close();
				}
			} catch (IOException e) {
				System.err.println("== Unable to properly close sockets/IOstreams ==\n\n");
				e.printStackTrace();
			}
		}
	}


	static public void main(String args[]) {
		try {                                                        
			int serverPort = 16405;

			if (args.length == 1) {
				serverPort = Integer.parseInt(args[0]);
			}
			else if (args.length > 1) {
				System.err.println("== You've entered too many arguments. Only one " +
						"port number must be passed ==");
				System.exit(1);
			} else {
				System.err.println("== Usage: java HttpServer <port> ==");
				System.exit(1);
			}

			while(true) {
				// start listening on the specified port                                       
				ServerSocket ss = new ServerSocket(serverPort);

				// wait for and accept a connection from the client
				Socket cs = ss.accept();

				// create and start a new thread to handle this request
				Thread t1 = new Thread(new HttpServer().new HandleRequest(cs, ss));
				t1.start();

				ss.close();
			}

		} catch (Exception e) {
			System.err.println("== Usage: java HttpServer <port> ==");
		}

	}
}
