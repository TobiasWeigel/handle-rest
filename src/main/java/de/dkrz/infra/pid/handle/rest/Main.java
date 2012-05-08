package de.dkrz.infra.pid.handle.rest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.PrivateKey;

import net.handle.hdllib.HandleException;
import net.handle.hdllib.Util;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public final class Main {

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		if (args.length < 4) {
			System.out.println("Parameters: <port number> <admin handle> <handle key index> <key file> <cipher>");
			System.out.println("");
			System.out.println("  <port number>      - TCP port to run the server on");
			System.out.println("  <admin handle>     - NA Handle (e.g. 0.NA/12345)");
			System.out.println("  <handle key index> - Key index in the admin handle (typically 300)");
			System.out.println("  <key file>         - private key file of the Handle admin");
			System.out.println("  <cipher>           - optionally, the cipher the private keyfile is secured with");
			System.exit(1);
		}
		// parse arguments, read files etc.
		int port = 0;
		try {
			port = Integer.parseInt(args[0]);
		} catch (NumberFormatException exc) {
			System.err.println("Illegal port number!");
			System.exit(10);
		}
		String adminHandle = args[1];
		int adminHandleKeyIndex = 0;
		try {
			adminHandleKeyIndex = Integer.parseInt(args[2]);
		} catch (NumberFormatException exc) {
			System.err.println("Illegal key index!");
			System.exit(10);
		}

		byte[] keyfilecontents = null;
		try {
			keyfilecontents = readKeyFromFile(args[3]);
		} catch (IOException exc) {
			System.err.println("Error while reading from private key file!");
			exc.printStackTrace(System.err);
			System.exit(10);
		}

		// decrypt key if necessary
		byte[] cipher = null;
		if (Util.requiresSecretKey(keyfilecontents)) {
			if (args.length < 5) {
				System.err.println("The key file is encrypted. Please provide a cipher as optional paramter!");
				System.exit(11);
			}
			cipher = args[4].getBytes();
		}
		// set up server and servlets
		Server server = new Server(port);

		ServletContextHandler context = new ServletContextHandler(
				ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		server.setHandler(context);

		HandleAuthorizationInfo authInfo = new HandleAuthorizationInfo(
				adminHandle, adminHandleKeyIndex, keyfilecontents, cipher);
		
		HandleSystemEndpointServlet handleServlet = null;
		
		try {
			handleServlet = new HandleSystemEndpointServlet(
					authInfo);			
		}
		catch (HandleException exc) {
			System.err.println("Error during servlet initialization!\nThis may be caused by a failure in key authentication. Did you provide the correct key file, admin handle and handle index?");
			exc.printStackTrace(System.err);
			System.exit(20);
		}
		
		try {
			context.addServlet(new ServletHolder(handleServlet), "/handle/*");
			// start the server
			server.start();
			server.join();
		}
		catch (Exception exc) {
			System.err.println("Error during server startup or operation:");
			exc.printStackTrace(System.err);
			System.exit(20);
		}

	}

	private static byte[] readKeyFromFile(String fn) throws IOException {
		File f = new File(fn);
		if (f.length() > 1024) {
			throw new IOException("Secret Key file too large / illegal format!");
		}
		FileInputStream fis;
		byte[] res = new byte[(int) f.length()];
		fis = new FileInputStream(new File(fn));
		fis.read(res);
		fis.close();
		return res;
	}

}
