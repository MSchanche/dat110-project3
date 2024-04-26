package no.hvl.dat110.util;


/**
 * @author tdoy
 * dat110 - project 3
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import no.hvl.dat110.chordoperations.ChordLookup;
import no.hvl.dat110.middleware.Node;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.middleware.Message;
import no.hvl.dat110.rpc.interfaces.NodeInterface;

import static no.hvl.dat110.util.Hash.hashOf;

public class FileManager {
	
	private static final Logger logger = LogManager.getLogger(FileManager.class);
	
	private BigInteger[] replicafiles;
	private int numReplicas;
	private NodeInterface chordnode;
	private String filepath;
	private String filename;
	private BigInteger hash;
	private byte[] bytesOfFile;
	private String sizeOfByte;
	
	private Set<Message> activeNodesforFile = null;
	
	public FileManager(NodeInterface chordnode) throws RemoteException {
		this.chordnode = chordnode;
	}
	
	public FileManager(NodeInterface chordnode, int N) throws RemoteException {
		this.numReplicas = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}
	
	public FileManager(NodeInterface chordnode, String filepath, int N) throws RemoteException {
		this.filepath = filepath;
		this.numReplicas = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}
	
	public void createReplicaFiles() {

		replicafiles = new BigInteger[numReplicas];

		for (int i = 0; i < numReplicas; i++) {

			String replicaFileName = filename + i;

			BigInteger replicaHash = hashOf(replicaFileName);

			replicafiles[i] = replicaHash;
		}
	}
	
    /**
     *
     * @param bytesOfFile
     * @throws RemoteException
     */
    public int distributeReplicastoPeers() throws RemoteException {

		// randomly appoint the primary server to this file replicas

		// Task1: Given a filename, make replicas and distribute them to all active peers such that: pred < replica <= peer
		// Task2: assign a replica as the primary for this file. Hint, see the slide (project 3) on Canvas
		// create replicas of the filename
		// iterate over the replicas
		// for each replica, find its successor (peer/node) by performing findSuccessor(replica)
		// call the addKey on the successor and add the replica
		// implement a logic to decide if this successor should be assigned as the primary for the file
		// call the saveFileContent() on the successor and set isPrimary=true if logic above is true otherwise set isPrimary=false
		// increment counter

			Random rnd = new Random();
			int index = rnd.nextInt(Util.numReplicas);

			int counter = 0;

			byte[] bytesOfFile; // Assume this contains the bytes of the file to replicate
			bytesOfFile = getBytesOfFile(); // You need to define this part based on how you access the file content

			NodeInterface chordnode; // Current node, should be defined in your class
			chordnode = getChordnode(); // You need to define this part based on your Chord implementation

			// Generate a hash for each of the replicas based on the filename and a replica index
			for (int i = 0; i < Util.numReplicas; i++) {
				// Compute the replica's ID using MD5 hash function
				BigInteger replica = hashOf(filename + i); // Assuming method 'hashOf' exists and is static

				NodeInterface successor = chordnode.findSuccessor(replica);
				if (successor == null) {
					// Handle the situation if a successor is not found.
					// For example, continue with the next replica or try to fix the Chord ring
					continue;
				}

				try {
					successor.addKey(replica);

					boolean isPrimary = (i == index);

					successor.saveFileContent(filename, replica, bytesOfFile, isPrimary);

					counter++;
				} catch (RemoteException e) {

				}
			}

			return counter;
    }
	
	/**
	 * 
	 * @param filename
	 * @return list of active nodes having the replicas of this file
	 * @throws RemoteException 
	 */
	public Set<Message> requestActiveNodesForFile(String filename) throws RemoteException {

		this.filename = filename;

		activeNodesforFile = new HashSet<Message>();

		createReplicaFiles();

		// Task: Given a filename, find all the peers that hold a copy of this file
		// generate the N replicas from the filename by calling createReplicaFiles()
		// iterate over the replicas of the file
		// for each replica, do findSuccessor(replica) that returns successor s.
		// get the metadata (Message) of the replica from the successor (i.e., active peer) of the file
		// save the metadata in the set activeNodesforFile.


		for (BigInteger replicaHash : replicafiles) {
			// Find the successor node holding this replica
			NodeInterface successor = getChordnode().findSuccessor(replicaHash);
			if (successor == null) {
				continue;
			}
			Message metadata = successor.getFilesMetadata(replicaHash);

			if (metadata != null) {
				activeNodesforFile.add(metadata);
			}
		}
		return activeNodesforFile;
	}
	/**
	 * Find the primary server - Remote-Write Protocol
	 * @return 
	 */
	public NodeInterface findPrimaryOfItem() {
		for (Message metadata : activeNodesforFile) {
			if (metadata.isPrimaryServer()) {
				return Util.getProcessStub(String.valueOf(metadata.getNodeID()), metadata.getPort());
			}
		}
		return null;
	}
	
    /**
     * Read the content of a file and return the bytes
     * @throws IOException 
     * @throws NoSuchAlgorithmException 
     */
    public void readFile() throws IOException, NoSuchAlgorithmException {
    	
    	File f = new File(filepath);
    	
    	byte[] bytesOfFile = new byte[(int) f.length()];
    	
		FileInputStream fis = new FileInputStream(f);
        
        fis.read(bytesOfFile);
		fis.close();
		
		//set the values
		filename = f.getName().replace(".txt", "");		
		hash = hashOf(filename);
		this.bytesOfFile = bytesOfFile;
		double size = (double) bytesOfFile.length/1000;
		NumberFormat nf = new DecimalFormat();
		nf.setMaximumFractionDigits(3);
		sizeOfByte = nf.format(size);
		
		logger.info("filename="+filename+" size="+sizeOfByte);
    	
    }
    
    public void printActivePeers() {
    	
    	activeNodesforFile.forEach(m -> {
    		String peer = m.getNodeName();
    		String id = m.getNodeID().toString();
    		String name = m.getNameOfFile();
    		String hash = m.getHashOfFile().toString();
    		int size = m.getBytesOfFile().length;
    		
    		logger.info(peer+": ID = "+id+" | filename = "+name+" | HashOfFile = "+hash+" | size ="+size);
    		
    	});
    }

	/**
	 * @return the numReplicas
	 */
	public int getNumReplicas() {
		return numReplicas;
	}
	
	/**
	 * @return the filename
	 */
	public String getFilename() {
		return filename;
	}
	/**
	 * @param filename the filename to set
	 */
	public void setFilename(String filename) {
		this.filename = filename;
	}
	/**
	 * @return the hash
	 */
	public BigInteger getHash() {
		return hash;
	}
	/**
	 * @param hash the hash to set
	 */
	public void setHash(BigInteger hash) {
		this.hash = hash;
	}
	/**
	 * @return the bytesOfFile
	 */ 
	public byte[] getBytesOfFile() {
		return bytesOfFile;
	}
	/**
	 * @param bytesOfFile the bytesOfFile to set
	 */
	public void setBytesOfFile(byte[] bytesOfFile) {
		this.bytesOfFile = bytesOfFile;
	}
	/**
	 * @return the size
	 */
	public String getSizeOfByte() {
		return sizeOfByte;
	}
	/**
	 * @param sizeOfByte the size to set
	 */
	public void setSizeOfByte(String sizeOfByte) {
		this.sizeOfByte = sizeOfByte;
	}

	/**
	 * @return the chordnode
	 */
	public NodeInterface getChordnode() {
		return chordnode;
	}

	/**
	 * @return the activeNodesforFile
	 */
	public Set<Message> getActiveNodesforFile() {
		return activeNodesforFile;
	}

	/**
	 * @return the replicafiles
	 */
	public BigInteger[] getReplicafiles() {
		return replicafiles;
	}

	/**
	 * @param filepath the filepath to set
	 */
	public void setFilepath(String filepath) {
		this.filepath = filepath;
	}
}
