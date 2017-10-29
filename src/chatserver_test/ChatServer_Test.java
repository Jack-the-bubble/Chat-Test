/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chatserver_test;

import java.io.*;
import java.net.*;
import java.util.*;
import sun.management.jdp.JdpJmxPacket;

/**
 *Prosty serwer pogawędek współpracujący z prostym klientem do prowadzenia pogawędek.
 * 
 * Nie obsługuje 'anonimowych pseudonimów' w żadnej postaci- to słuszne rozwiązanie zważywszy, jak wiele osób 
 * w przeszłości niewłaśiwie korzystało z anonimowych pogawędek.
 * 
 * @author Boba
 */
public class ChatServer_Test 
{
    /**Nazwa serwera w kontaktach */
    protected final static String CHATMASTER_ID="ChatMaster";
    /** Separator między nazwą a komunikatem*/
    protected final static String SEP=": ";
    /**Gniazdo serwera*/
    protected ServerSocket servSock;
    /**Lista podłączonych klientów*/
    protected ArrayList clients;
    /** flaga testowania*/
    private static boolean DEBUG=false;
    
    /**Metoda main tworzy obiekt ChatServer i uruchamia go, a działanie metody runServer nigdy się nie powinno zakończyć
     * @param args the command line arguments
     */
    public static void main(String[] argv) 
    {
        // TODO code application logic here
        System.out.println("Trwa uruchamianie Serwera ChatServer 0.1...");
        if(argv.length==1 && argv[0].equals("-debug"))
            DEBUG=true;
        ChatServer_Test w =  new ChatServer_Test();
        w.runServer();
        System.err.println("**Blad* Chatserver 0.1 zamykanie...");
    }


    
    /** Tworzenie i uruchamianie usługi */
    public ChatServer_Test() 
    {
          clients = new ArrayList();
          
          try 
          {
              servSock= new ServerSocket(Chat.PORTNUM);
              System.err.println("ChatSerwer działa na porcie"+ Chat.PORTNUM);
          } catch (IOException e)
          {
              log("Blad wejscia-wyjscia w serwerze ChatSerwer.<init>");
              System.exit(0);
          }
    }  
      
    public void runServer()
    {
        try 
        {
            while(true)
            {
                Socket us= servSock.accept();
                String hostname= us.getInetAddress().getHostName();
                System.out.println("Odebrano polaczenie z "+ hostname);
                ChatHandler cl = new ChatHandler (us, hostname);
                synchronized (clients)
                {
                    clients.add(cl);
                    cl.start();
                    if(clients.size()==1)
                        cl.send(CHATMASTER_ID, "Witam! Jestes tu pierwszy");
                    else
                    {
                        cl.send(CHATMASTER_ID, "Witam! Jestes "+clients.size()+" -im/-ym uzytkownikiem");
                    }
                }
            }
        } catch (IOException e)
        {
            log("Blad wejscia-wyjscia w metodzie runServer: "+ e);
            System.exit(0);
        }
    }
    
    /**funkcja do ulatwiania wyświetlania*/
    protected void log(String s)
    {
        System.out.println(s);
    }
    
    /**Klasa wewnętrzna obsługująca jedno połączenie*/
    protected class ChatHandler extends Thread 
    {
        /**gniazdo klienta */
        protected Socket clientSock;
        /** obiekt BufferedReader służący do odczytu danych z gniazda*/
        protected BufferedReader is;
        /** Obiekt PrintWriter służący do zapisu komunikatów w gnieździe.*/
        protected PrintWriter pw;
        /** Komputer, na którym działa klient*/
        protected String clientIP;
        /** Nazwa*/
        protected String login;
        
        /** konstruktor obiektu*/
        public ChatHandler(Socket sock, String clnt) throws IOException
        {
            clientSock = sock;
            clientIP=clnt;
            is= new BufferedReader(new InputStreamReader(sock.getInputStream()));
            pw=new PrintWriter(sock.getOutputStream(), true);
        }
        
        /** Każdy ChatHandler jest wyjątkiem klasy Thread, więc musi mieć metodę run(),
         * która obsługuje to połączenie.
         */
        public void run()
        {
            String line;
            try 
            {
                while((line==is.readLine())!= null)
                {
                    char c = line.charAt(0);
                    line=line.substring(1);
                    switch (c)
                    {
                        case Chat.CMD_LOGIN:
                            if (!Chat.isValidLoginName(line))
                            {
                                send(CHATMASTER_ID, "NAZWA "+ line + " jest niewlasciwa");
                                log("Niewlasciwa nazwa z "+ clientIP);
                                continue;
                            }
                            login=line;
                            broadcast(CHATMASTER_ID, login+" sie dolaczyl, aktualnie jest "+ clients.size()+ " uzytkownikow");
                            break;
                        case Chat.CMD_MESG:
                            if(login==null)
                            {
                                send(CHATMASTER_ID, "prosze sie najpierw zalogowac");
                                continue;
                            }
                            int where= line.indexOf(Chat.SEPARATOR);
                            String recip=line.substring(0, where+1);
                            log("WIAD: "+ login+" --> "+ recip+ " : "+ mesg);
                            ChatHandler cl= lookup(recip);
                            if (cl == null)
                            {
                                psend(CHATMASTER_ID, recip+" nie jest zalogowany");
                                
                            }
                            else
                            {
                                cl.psend(login, mesg);
                            }
                            break;
                        case Chat.CMD_QUIT:
                            broadcast(CHATMASTER_ID, "Zegnam "+ login+ " @ "+ clientIP());
                            close();
                            return; //Koniec działania
                            
                        case Chat.CMD_BCAST:
                            if (login != null)
                            {
                                broadcast(login, line);
                            }
                            else
                            {
                                log("B<L OD "+ clientIP);
                            }
                            break;
                        default:
                            log("Nieznane polecenie "+ c + " z "+ login+ " @ "+clientIP);
                    }
                }
            }catch(IOException e){
                log("Blad wejscia-wyjjscia: "+ e);
            } finally{
                /** Połączenie zamknięte, zatem końćzymy, teraz już nie można wysyłać wiadomości pożegnalnej, chyba że 
                 * zaimplementujemy prosty protokół poleceń */
                System.out.println(login + SEP + " Wszystko gotowe");
                synchronized(clients)
                {
                    clients.remove(this);
                    if (clients.size()==0)
                    {
                        System.out.println(CHATMASTER_ID + SEP + "nikogo nie ma w poblizu...");
                    }
                    else if (clients.size()==1)
                    {
                        ChatHandler last = (ChatHandler)clients.get(0);
                        last.send(CHATMASTER_ID, "Hej, nikogo oprocz ciebie nie ma");
                    }
                    else
                    {
                        broadcast(CHATMASTER_ID, "Aktualnie jest podlaczonych "+ clients.size() + " uzytkownikow");
                        
                    }
                }
                 
            }
        }
        
        protected void close()
        {
            if (clientSock == null)
            {
                log("Zamykamy nieotwarte polaczenie");
                return;
            }
        }
        try 
        {
            clientSock.close();
            clientSock=null;
        } catch (IOException e) {
            log("Blad podczas zamykania polaczenia  z " + clientIP);
        }
        /** Wysyłanie jednego komunikatu do użytkownika   */
        public void send(String sender, String mesg)
        {
            pw.println(sender+SEP+mesg);
        }
        
        /**Wysyłanie komunikatu prywatnego */
        protected void psend(String sender, String msg)
        {
            send("<*"+sender+"*>", msg);
        }
        
        /**Wysyłanie jednego komunikatu do wszystkich użytkowników*/
        public void broadcast(String sender, String mesg)
        {
            System.out.println("Transmisja ogolna "+ sender + SEP+ mesg);
            for (int i =0; i<clients.size();i++){
                ChatHandler sib = (ChatHandler)clients.get(i);
                if (DEBUG)
                    System.out.println("Wysylanie do "+ sib);
                sib.send(sender, mesg);
            }
            if(DEBUG) System.out.println("Transmisja wykonana");
        }
        
        protected ChatHandler lookup(String nick){
            synchronized(clients)
            {
                for(int i=0; i<clients.size(); i++)
                {
                    ChatHandler cl = (ChatHandler)clients.get(i);
                    if (cl.login.equals(nick))
                        return cl;
                }
            }
            return null;
        }
        
        /**Zapisanie obiektu w formie łańcucha znakówv */
        public String toString()
        {
            return "ChatHandler["+login+"]";
        }
    }
        
}
    
