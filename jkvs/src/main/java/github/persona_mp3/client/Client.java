package github.persona_mp3.client;

import github.persona_mp3.lib.protocol.Protocol;
import github.persona_mp3.lib.protocol.Request;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;


public class Client {
	private static Logger logger = LogManager.getLogger(Client.class);
	private static Protocol protocol = new Protocol();

	public static void main(String[] args) {
		System.out.println("client-executable running");
	}
}
