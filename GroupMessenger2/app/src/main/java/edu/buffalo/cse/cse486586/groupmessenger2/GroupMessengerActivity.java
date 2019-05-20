package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import java.net.UnknownHostException;
import java.util.*;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT[] = {"11108","11112","11116","11120","11124"};
    static final int SERVER_PORT = 10000;
    static int key_cntr = 0;
    static int counter =0;
    static int client_counter = 0;
    static double final_sequene=0;

    /*
    Defining the holding queue, delivery queue
    hmap is for storing the proposed sequence for msgs
    hmap_failure is to store the details of avd which have failed
    hmap_time stores the last received time of msgs from each avds
     */
    PriorityQueue<Msgholder> priorityQueue = new PriorityQueue<Msgholder>(100, new SequenceComparator());
    PriorityQueue<Msgholder> priorityQueue_delivery = new PriorityQueue<Msgholder>(100, new SequenceComparator());
    HashMap<String, Double> hmap = new HashMap< String, Double>();
    HashMap<String, String> hmap_failure = new HashMap<String, String>();
    HashMap<String, Date> hmap_time = new HashMap<String, Date>();

    /*
    This class is to override the compare() method of Comparator
    so that it sorts the elements in ascending order
    Referenced from : https://www.geeksforgeeks.org/implement-priorityqueue-comparator-java/
     */
    class SequenceComparator implements Comparator<Msgholder>{

        public int compare(Msgholder m1, Msgholder m2) {
            if (m1.sequence > m2.sequence)
                return 1;
            else if (m1.sequence < m2.sequence)
                return -1;
            return 0;
        }
    }

    /*
    This class is used to create the objects for the priority queues
    comprising of the message and the sequence numbers
    Referenced from : https://www.geeksforgeeks.org/implement-priorityqueue-comparator-java/
     */
    class Msgholder {
        public String receivedmsg;
        public double sequence;

        //parameterised constructor
        public Msgholder(String receivedmsg, double sequence) {

            this.receivedmsg = receivedmsg;
            this.sequence = sequence;
        }
        //method to retrieve string message from the class object
        public String getmsg() {
            return receivedmsg;
        }
        //method to retrieve sequence number from the class object
        public Double getseq() {
            return sequence; }
        //method to override the .equals class of Proiority queue remove(object)
        public boolean equals(Object o){
            Msgholder newobject =  (Msgholder)(o);
            if((this.receivedmsg.equals(newobject.receivedmsg))&&(this.sequence==newobject.sequence)){
                return  true;
            }
            return false;
        }
    }

    /*
    Initializing the URI object
     */
    Uri mUri = buildUri2("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");

    public Uri buildUri2(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        try {

            System.out.println(myPort);

            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
        Referenced from: PA1
        https://developer.android.com/guide/topics/ui/controls/button#java
         */
        final EditText et = (EditText) findViewById(R.id.editText1);
        Button button = (Button) findViewById(R.id.button4);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Display text on screen in response to button click
                String msg = et.getText().toString() + "\n";
                et.setText("");
                tv.append("\t" + msg);

                //Calling the client function on clicking the send button
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);

            }
        });
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected synchronized Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket socket = null;


            try {
                while(true) {
                    try {
                        /*
                        The hmap_failure hash map is checked for any failed ports. For any failed port, the
                        msgs from that port are retrieved using the hashmap hmap and deleted from priority queue.
                         */
                        if (hmap_failure.isEmpty() == false && hmap.isEmpty()==false) {
                            for (Map.Entry<String, String> entry_failure : hmap_failure.entrySet()) {
                                String key = entry_failure.getKey();
                                for (String e : hmap.keySet()) {
                                    boolean iskeyretrieved = e.contains(key);
                                    System.out.println("Key substring matched or not: " + iskeyretrieved);
                                    if (e.contains(key)) {
                                        Double sequence = hmap.get(e);
                                        Msgholder obj = new Msgholder(e, sequence);
                                        //System.out.println("Failed instances' objects msgs" + obj.getmsg());
                                        //System.out.println("Failed instances' objects sequence" + obj.getseq());
                                        //System.out.println("Failed instances' hmap sequence" + sequence);
                                        boolean isitremoved = priorityQueue.remove(obj);
                                        System.out.println("Return value after removing obj: " + isitremoved);
                                    }
                                }
                                hmap_failure.remove(key);
                            }

                        }

                        /*
                        This section checks if there are any elements in the delivery/holding queue to publish
                         */
                        if (priorityQueue_delivery.size() > 0 && priorityQueue.size() > 0) {

                            ArrayList<Msgholder> list1 = new ArrayList<Msgholder>(priorityQueue);
                            ArrayList<Msgholder> list2 = new ArrayList<Msgholder>(priorityQueue_delivery);
                            if (list1.size() > 1 && list2.size() > 1) {
                                Collections.sort(list1, new SequenceComparator());
                                Collections.sort(list2, new SequenceComparator());
                            }
                            System.out.println("Sorting completed");
                            int i = 0;
                            /*
                        If the element at the head of the holding queue is same as that of delivery queue,
                        the message is delivered and the object is removed from both the queues.
                        If the element is not equal, then no msg is delivered.
                         */
                            while (i < list1.size() && i < list2.size() && list1.get(i).equals(list2.get(i))) {
                                Msgholder object_final = priorityQueue_delivery.peek();
                                String msg_tobepublished = object_final.getmsg();
                                String msg_publish = msg_tobepublished.substring(0, msg_tobepublished.lastIndexOf("_"));
                                publishProgress(msg_publish);
                                boolean isremoved_final = priorityQueue.remove(object_final);
                                boolean isremoved_final2 = priorityQueue_delivery.remove(object_final);
                                System.out.println("Return value after remove from fina loop: " + isremoved_final);
                                System.out.println("Return value after remove from fina loop: " + isremoved_final2);
                                i++;
                            }
                        }


                        /*
                         * Receiving msg from Client and passing them to onProgressUpdate
                         * Referenced from https://docs.oracle.com/javase/7/docs/api/java/io/ObjectInputStream.html
                         */
                        socket = serverSocket.accept();
                        InputStream inputStream = socket.getInputStream();
                        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                        String msg = (String) objectInputStream.readObject();
                        socket.setSoTimeout(500*2);

                        System.out.println("Printing the msgs received on Server side");
                        System.out.println(msg);

                        /*
                        This section retreives the received msg with an identifier containing the
                        port from which the msg has been received along with its own port details
                        to derive the process ID. Taking the maximum out of all previously proposed counters and
                        final proposals sent by the server along with the no of msgs sent by the client, the proposes
                        sequence is sent to the client.
                         */
                        if (msg.contains("_initial")) {
                            msg = msg.substring(0, msg.lastIndexOf("_"));
                            counter = (int) Math.max(Math.max(client_counter, counter), Math.round(final_sequene));
                            counter++;
                            int process_id = Integer.parseInt(msg.substring(msg.lastIndexOf("_") + 10, msg.lastIndexOf("_") + 11));
                            String proposed_seq_str = counter + "." + process_id;
                            double proposed_seq = Double.parseDouble(proposed_seq_str);
                            String hmapkey = msg.substring(msg.lastIndexOf("_") + 1);
                            String proposal_msg = Double.toString(proposed_seq) + "_" + hmapkey;
                            Msgholder queued_msg = new Msgholder(msg, proposed_seq);
                            priorityQueue.add(queued_msg);
                            hmap.put(msg, proposed_seq);

                            OutputStream outputStream = socket.getOutputStream();
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
                            objectOutputStream.writeObject(proposal_msg);
                            objectOutputStream.flush();

                            /*
                            This section accepts the final proposed sequence and updates the same on holding and
                            priority queue.
                             */

                        }
                        else if (msg.contains("_final")) {
                            System.out.println("Final step reached :" + msg);
                            msg = msg.substring(0, msg.lastIndexOf("_"));
                            double final_seq = Double.parseDouble(msg.substring(msg.lastIndexOf("_") + 1));
                            final_sequene = final_seq;

                            String message = msg.substring(0, msg.lastIndexOf("_"));
                            String identifier = message.substring(message.lastIndexOf("_") + 1);

                            Double initial_seq = hmap.get(message);
                            Msgholder object1 = new Msgholder(message, initial_seq);
                            boolean isremoved = priorityQueue.remove(object1);
                            System.out.println("Return value after remove: " + isremoved);

                            Msgholder object2 = new Msgholder(message, final_seq);
                            priorityQueue.add(object2);
                            priorityQueue_delivery.add(object2);
                            hmap.remove(message);

                            /*
                            Storing the time at which the last final sequence was received from each port
                             */
                            Date date = new Date();
                            hmap_time.put(identifier.substring(0, 5), date);

                            /*
                            Sending the acknowledgement msg to client to close socket
                             */
                            OutputStream outputStream = socket.getOutputStream();
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
                            String ack_msg = "ACK";
                            objectOutputStream.writeObject(ack_msg);
                            objectOutputStream.flush();

                        }
                        /*
                        Checking if for any port the last final sequence receival was before 9000 miliseconds
                        then considering that node to fail.
                         */
                        for (Map.Entry<String, Date> entry : hmap_time.entrySet()) {
                            Date date_current = new Date();
                            long miliseconds = date_current.getTime() - entry.getValue().getTime();
                            if (miliseconds > 9000) {
                                System.out.println("Entered here for inserting miliseconds: " + miliseconds + " " + entry.getKey());
                                hmap_failure.put(entry.getKey(), "Failed");
                            }
                        }
                    } catch (SocketException exception) {
                        /*
                        Checking if for any port the last final sequence receival was before 9000 miliseconds
                        then considering that node to fail.
                         */
                        for (Map.Entry<String, Date> entry : hmap_time.entrySet()) {
                            Date date_current = new Date();
                            long miliseconds = date_current.getTime() - entry.getValue().getTime();

                            if (miliseconds > 9000) {
                                System.out.println("Entered here for inserting miliseconds: " + miliseconds + " " + entry.getKey());
                                hmap_failure.put(entry.getKey(), "Failed");
                            }
                        }
                        exception.printStackTrace();

                    } catch (OptionalDataException exception) {
                        /*
                        Checking if for any port the last final sequence receival was before 9000 miliseconds
                        then considering that node to fail.
                         */
                        for (Map.Entry<String, Date> entry : hmap_time.entrySet()) {
                            Date date_current = new Date();
                            long miliseconds = date_current.getTime() - entry.getValue().getTime();

                            if (miliseconds > 9000) {
                                System.out.println("Entered here for inserting miliseconds: " + miliseconds + " " + entry.getKey());
                                hmap_failure.put(entry.getKey(), "Failed");
                            }
                        }
                        exception.printStackTrace();
                    } catch (StreamCorruptedException exception) {
                        /*
                        Checking if for any port the last final sequence receival was before 9000 miliseconds
                        then considering that node to fail.
                         */
                        for (Map.Entry<String, Date> entry : hmap_time.entrySet()) {
                            Date date_current = new Date();
                            long miliseconds = date_current.getTime() - entry.getValue().getTime();

                            if (miliseconds > 9000) {
                                System.out.println("Entered here for inserting miliseconds: " + miliseconds + " " + entry.getKey());
                                hmap_failure.put(entry.getKey(), "Failed");
                            }
                        }
                        exception.printStackTrace();
                    } catch (IOException exception) {
                        /*
                        Checking if for any port the last final sequence receival was before 9000 miliseconds
                        then considering that node to fail.
                         */
                        for (Map.Entry<String, Date> entry : hmap_time.entrySet()) {
                            Date date_current = new Date();
                            long miliseconds = date_current.getTime() - entry.getValue().getTime();

                            if (miliseconds > 9000) {
                                System.out.println("Entered here for inserting miliseconds: " + miliseconds + " " + entry.getKey());
                                hmap_failure.put(entry.getKey(), "Failed");
                            }
                        }
                        exception.printStackTrace();
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            return null;

        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            TextView localTextView = (TextView) findViewById(R.id.textView1);
            localTextView.append("\n");

            /*
            Following code inserts the received message using Content Resolver into internal file storage
            using key_cntr value as the file name
             */
            ContentValues cv = new ContentValues();
            cv.put("key", Integer.toString(key_cntr));
            cv.put("value", strReceived);
            getContentResolver().insert(mUri, cv);
            System.out.println("File Created "+ strReceived + "with name as : "+ key_cntr);
            key_cntr++;

            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected synchronized Void   doInBackground(String... msgs) {

            String msgToSend = msgs[0];
            msgToSend = msgToSend.replace("\n", "").replace("\r", "");
            String ports[] ={"PORT0","PORT1","PORT2","PORT3","PORT4"};
            //stores all received sequences
            ArrayList<Double> proposal_queue = new ArrayList<Double>();
            //takes maximum of all previously seen sequences and adds 1 to it
            client_counter = (int) Math.max(Math.max(client_counter,counter),Math.round(final_sequene))+1;


            for (int i = 0; i < REMOTE_PORT.length; i++) {
                try {
                    if (msgs[1].equals(REMOTE_PORT[i])) {
                        String str = Integer.toString(client_counter)+"."+ ports[i].charAt(ports[i].length()-1);
                        proposal_queue.add(Double.parseDouble(str));
                    }
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(REMOTE_PORT[i]));
                    socket.setSoTimeout(500*3);

                    String msg_port = msgToSend  + "_" + msgs[1] + ports[i] + client_counter + "_" + "initial";
                    OutputStream outputStream = socket.getOutputStream();
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
                    objectOutputStream.writeObject(msg_port);
                    objectOutputStream.flush();

                    /*
                    Receiving the proposed sequence for the msg sent
                     */
                    InputStream inputStream = socket.getInputStream();
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                    String msg_proposal = (String) objectInputStream.readObject();

                    /*
                     * Checking if proposed sequence message is received from  server
                     */
                    if (msg_proposal.contains(msgs[1] + ports[i]+client_counter)){
                        Double proposal = Double.parseDouble(msg_proposal.substring(0, msg_proposal.indexOf("_")));
                        proposal_queue.add(proposal);
                    }
                }
                catch (SocketTimeoutException e) {
                    hmap_failure.put(REMOTE_PORT[i],"Failed");
                    System.out.println("Failure handled at client's side"+ REMOTE_PORT[i] +hmap_failure.get(REMOTE_PORT[i]));
                    e.printStackTrace();
                } catch (OptionalDataException e) {
                    e.printStackTrace();
                } catch (SocketException e) {
                    hmap_failure.put(REMOTE_PORT[i],"Failed");
                    System.out.println("Failure handled at client's side"+ REMOTE_PORT[i]+ hmap_failure.get(REMOTE_PORT[i]));
                    e.printStackTrace();
                } catch (EOFException e) {
                    hmap_failure.put(REMOTE_PORT[i],"Failed");
                    System.out.println("Failure handled at client's side"+ REMOTE_PORT[i]+ hmap_failure.get(REMOTE_PORT[i]));
                    e.printStackTrace();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (StreamCorruptedException e) {
                    hmap_failure.put(REMOTE_PORT[i],"Failed");
                    System.out.println("Failure handled at client's side"+ REMOTE_PORT[i]+ hmap_failure.get(REMOTE_PORT[i]));
                    e.printStackTrace();
                } catch (IOException e) {
                    hmap_failure.put(REMOTE_PORT[i],"Failed");
                    System.out.println("Failure handled at client's side"+ REMOTE_PORT[i]+ hmap_failure.get(REMOTE_PORT[i]));
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }

            /*
            Selecting the maximum of all the received proposals
             */
            Collections.sort(proposal_queue, Collections.reverseOrder());
            String finalseq_str = String.valueOf(proposal_queue.get(0));
            System.out.println(finalseq_str);


            for (int i = 0; i < REMOTE_PORT.length; i++) {
                try {
                    String msg_fialseq= msgToSend  + "_" + msgs[1] + ports[i] + client_counter +"_"+finalseq_str+"_"+"final";

                    /*
                    Sending the final sequence for the msg
                     */
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(REMOTE_PORT[i]));
                    socket.setSoTimeout(500*3);

                    OutputStream outputStream = socket.getOutputStream();
                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
                    objectOutputStream.writeObject(msg_fialseq);
                    objectOutputStream.flush();
                    System.out.println("msg sent including final proposal");

                    InputStream inputStream = socket.getInputStream();
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                    String msg_ack = (String) objectInputStream.readObject();
                    if (msg_ack.contains("ACK")) {
                        socket.close();
                    }
                }
                catch (SocketTimeoutException e) {
                    hmap_failure.put(REMOTE_PORT[i],"Failed");
                    System.out.println("Failure handled at client's side"+ REMOTE_PORT[i]+  hmap_failure.get(REMOTE_PORT[i]));
                    e.printStackTrace();
                } catch (OptionalDataException e) {
                    e.printStackTrace();
                } catch (SocketException e) {
                    hmap_failure.put(REMOTE_PORT[i],"Failed");
                    System.out.println("Failure handled at client's side"+ REMOTE_PORT[i]+ hmap_failure.get(REMOTE_PORT[i]));
                    e.printStackTrace();
                } catch (EOFException e) {
                    hmap_failure.put(REMOTE_PORT[i],"Failed");
                    System.out.println("Failure handled at client's side"+ REMOTE_PORT[i]+ hmap_failure.get(REMOTE_PORT[i]));
                    e.printStackTrace();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (StreamCorruptedException e) {
                    hmap_failure.put(REMOTE_PORT[i],"Failed");
                    System.out.println("Failure handled at client's side"+ REMOTE_PORT[i]+ hmap_failure.get(REMOTE_PORT[i]));
                    e.printStackTrace();
                } catch (IOException e) {
                    hmap_failure.put(REMOTE_PORT[i],"Failed");
                    System.out.println("Failure handled at client's side"+ REMOTE_PORT[i]+ hmap_failure.get(REMOTE_PORT[i]));
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
