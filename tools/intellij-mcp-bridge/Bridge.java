import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Forwards MCP over stdin/stdout to the Claude Code plugin's WebSocket server in a local IntelliJ.
 * Run with {@code java tools/intellij-mcp-bridge/Bridge.java [--workspace PATH] [--probe]}.
 */
public final class Bridge {

    private static final String Description =
            "Forward MCP stdio to the local IntelliJ Claude Code plugin WebSocket.";
    private static final String Usage = "usage: Bridge.java [-h] [--workspace WORKSPACE] [--probe]";
    private static final Duration ConnectTimeout = Duration.ofSeconds(5);
    private static final long ProbeTimeoutSeconds = 20;
    private static final String Closed = "\u0000closed";

    public static void main(String[] args) {
        try {
            Options Opts = Options.Parse(args);
            Connection Link = Connect(Opts.Workspace());
            if (Opts.Probe()) Probe(Link); else Forward(Link);
            Link.Close();
        } catch (UsageError Error) {
            System.err.println(Usage);
            System.err.println("Bridge.java: error: " + Error.getMessage());
            System.exit(2);
        } catch (Exception Error) {
            System.err.println(Error.getMessage());
            System.exit(1);
        }
    }

    private record Options(String Workspace, boolean Probe) {

        static Options Parse(String[] Args) {
            String Workspace = Path.of("").toAbsolutePath().toString();
            boolean Probe = false;
            for (int I = 0; I < Args.length; I++) {
                String Arg = Args[I];
                if (Arg.equals("-h") || Arg.equals("--help")) PrintHelpAndExit();
                else if (Arg.equals("--probe")) Probe = true;
                else if (Arg.startsWith("--workspace=")) Workspace = Arg.substring("--workspace=".length());
                else if (Arg.equals("--workspace") && I + 1 < Args.length) Workspace = Args[++I];
                else if (Arg.equals("--workspace")) throw new UsageError("argument --workspace: expected one argument");
                else throw new UsageError("unrecognized arguments: " + Arg);
            }
            return new Options(Workspace, Probe);
        }

        private static void PrintHelpAndExit() {
            System.out.println(Usage + "\n\n" + Description + "\n\noptions:\n"
                    + "  -h, --help            show this help message and exit\n"
                    + "  --workspace WORKSPACE\n"
                    + "  --probe");
            System.exit(0);
        }
    }

    private static final class UsageError extends RuntimeException {
        UsageError(String Message) { super(Message); }
    }

    private record LockFile(Path Path, String Token) {}

    /** The same normalisation the IDE plugins write: forward slashes, lower case, WSL mounts mapped. */
    static String Normalized(String Path) {
        String Result = Path.replace('\\', '/').toLowerCase().replaceAll("/+$", "");
        if (Result.startsWith("/mnt/") && Result.length() > 6) {
            Result = Result.charAt(5) + ":" + Result.substring(6);
        }
        return Result;
    }

    /** Try every IntelliJ lock for this workspace, newest first; the first that answers wins. */
    private static Connection Connect(String Workspace) {
        for (LockFile Lock : CandidateLocks(Workspace)) {
            String Port = Lock.Path().getFileName().toString().replaceFirst("\\.lock$", "");
            try {
                return Connection.Open(Integer.parseInt(Port), Lock.Token());
            } catch (Exception Error) {
                String Detail = String.valueOf(Error.getMessage()).replace(Lock.Token(), "[redacted]");
                System.err.println("IntelliJ port " + Port + ": " + Error.getClass().getSimpleName() + ": " + Detail);
            }
        }
        throw new IllegalStateException(
                "No reachable IntelliJ MCP server for this workspace. Open IntelliJ with the Claude Code plugin enabled.");
    }

    private static List<LockFile> CandidateLocks(String Workspace) {
        Path Directory = Path.of(System.getProperty("user.home"), ".claude", "ide");
        List<LockFile> Locks = new ArrayList<>();
        if (!Files.isDirectory(Directory)) return Locks;
        try (DirectoryStream<Path> Paths = Files.newDirectoryStream(Directory, "*.lock")) {
            for (Path LockPath : Paths) {
                LockFile Lock = ReadLock(LockPath, Normalized(Workspace));
                if (Lock != null) Locks.add(Lock);
            }
        } catch (IOException Ignored) {
            return Locks;
        }
        Locks.sort(Comparator.comparing((LockFile L) -> ModifiedTime(L.Path())).reversed());
        return Locks;
    }

    /** The lock's auth token if it is an IntelliJ WebSocket server serving this workspace, else null. */
    private static LockFile ReadLock(Path LockPath, String Workspace) {
        try {
            Map<?, ?> Data = (Map<?, ?>) Json.Parse(Files.readString(LockPath, StandardCharsets.UTF_8));
            boolean IsIntelliJ = "ws".equals(Data.get("transport"))
                    && String.valueOf(Data.get("ideName")).contains("IntelliJ");
            if (!IsIntelliJ) return null;
            Object Folders = Data.get("workspaceFolders");
            boolean Serves = Folders instanceof List<?> List
                    && List.stream().anyMatch(F -> Normalized(String.valueOf(F)).equals(Workspace));
            if (!Serves || !(Data.get("authToken") instanceof String Token)) return null;
            return new LockFile(LockPath, Token);
        } catch (IOException | RuntimeException Unreadable) {
            return null;
        }
    }

    private static long ModifiedTime(Path File) {
        try {
            return Files.getLastModifiedTime(File).toMillis();
        } catch (IOException Error) {
            return 0;
        }
    }

    /** Initialise, list the tools, and read diagnostics if the server offers them. */
    private static void Probe(Connection Link) throws Exception {
        Map<String, Object> ClientInfo = Json.Object("name", "codex-intellij-bridge", "version", "1.0");
        Map<?, ?> Initialized = Link.Request(1, "initialize", Json.Object(
                "protocolVersion", "2024-11-05", "capabilities", Json.Object(), "clientInfo", ClientInfo));
        System.out.println(Json.Write(Json.Object("serverInfo", Initialized.get("serverInfo"))));
        Link.Send(Json.Write(Json.Object("jsonrpc", "2.0", "method", "notifications/initialized")));

        Map<?, ?> Tools = Link.Request(2, "tools/list", Json.Object());
        System.out.println(Json.Write(Tools));
        if (OffersTool(Tools, "getDiagnostics")) {
            Object Diagnostics = Link.Request(3, "tools/call",
                    Json.Object("name", "getDiagnostics", "arguments", Json.Object()));
            System.out.println(Json.Write(Json.Object("diagnostics", Diagnostics)));
        }
    }

    private static boolean OffersTool(Map<?, ?> Tools, String Name) {
        return Tools.get("tools") instanceof List<?> List
                && List.stream().anyMatch(T -> T instanceof Map<?, ?> Tool && Name.equals(Tool.get("name")));
    }

    /** Relay stdin lines to the server and server messages to stdout until either side closes. */
    private static void Forward(Connection Link) throws IOException {
        Thread Receiver = new Thread(() -> RelayToStdout(Link));
        Receiver.setDaemon(true);
        Receiver.start();

        BufferedReader Stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (String Line = Stdin.readLine(); Line != null; Line = Stdin.readLine()) {
            if (!Line.isBlank()) Link.Send(Line.strip());
        }
        ClientFinished = true;   // stdin closed: our own close is not a lost connection
    }

    private static volatile boolean ClientFinished;

    private static void RelayToStdout(Connection Link) {
        try {
            for (String Message = Link.Next(); !Closed.equals(Message); Message = Link.Next()) {
                System.out.println(Json.Write(Json.Parse(Message)));
                System.out.flush();
            }
        } catch (RuntimeException | InterruptedException Ended) {
            // Any failure ends the relay the same way a close does.
        }
        if (ClientFinished) return;
        System.err.println("IntelliJ connection closed; restart this MCP server to reconnect.");
        Runtime.getRuntime().halt(1);
    }

    /**
     * A minimal RFC 6455 client whose complete messages arrive, in order, on a queue. Hand-rolled
     * because java.net.http always sends a User-Agent, and the IDE servers close any connection
     * that carries one.
     */
    private static final class Connection {

        private static final String Guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private static final int OpContinuation = 0, OpText = 1, OpBinary = 2, OpClose = 8, OpPing = 9, OpPong = 10;

        private final BlockingQueue<String> Inbox = new LinkedBlockingQueue<>();
        private final SecureRandom Random = new SecureRandom();
        private Socket Socket;
        private InputStream In;
        private OutputStream Out;

        static Connection Open(int Port, String Token) throws IOException {
            Connection Link = new Connection();
            Link.Socket = new Socket();
            Link.Socket.connect(new InetSocketAddress("127.0.0.1", Port), (int) ConnectTimeout.toMillis());
            Link.Socket.setSoTimeout((int) ConnectTimeout.toMillis());
            Link.In = new BufferedInputStream(Link.Socket.getInputStream());
            Link.Out = Link.Socket.getOutputStream();
            Link.Handshake(Port, Token);
            Link.Socket.setSoTimeout(0);
            Thread Reader = new Thread(Link::ReadFrames);
            Reader.setDaemon(true);
            Reader.start();
            return Link;
        }

        private void Handshake(int Port, String Token) throws IOException {
            byte[] Nonce = new byte[16];
            Random.nextBytes(Nonce);
            String Key = Base64.getEncoder().encodeToString(Nonce);
            String Request = "GET / HTTP/1.1\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Host: 127.0.0.1:" + Port + "\r\n"
                    + "Sec-WebSocket-Key: " + Key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Protocol: mcp\r\n"
                    + "x-claude-code-ide-authorization: " + Token + "\r\n\r\n";
            Out.write(Request.getBytes(StandardCharsets.UTF_8));
            Out.flush();

            String Status = ReadLine();
            if (!Status.matches("HTTP/1\\.1 101\\b.*")) throw new IOException("Handshake status " + Status);
            Map<String, String> Headers = new LinkedHashMap<>();
            for (String Line = ReadLine(); !Line.isEmpty(); Line = ReadLine()) {
                int Colon = Line.indexOf(':');
                if (Colon > 0) Headers.put(Line.substring(0, Colon).trim().toLowerCase(), Line.substring(Colon + 1).trim());
            }
            if (!ExpectedAccept(Key).equals(Headers.get("sec-websocket-accept"))) {
                throw new IOException("Invalid challenge response");
            }
        }

        private static String ExpectedAccept(String Key) {
            try {
                byte[] Digest = MessageDigest.getInstance("SHA-1").digest((Key + Guid).getBytes(StandardCharsets.US_ASCII));
                return Base64.getEncoder().encodeToString(Digest);
            } catch (NoSuchAlgorithmException Impossible) {
                throw new IllegalStateException(Impossible);
            }
        }

        private String ReadLine() throws IOException {
            StringBuilder Line = new StringBuilder();
            for (int B = In.read(); B != '\n'; B = In.read()) {
                if (B < 0) throw new EOFException("Connection closed during handshake");
                if (B != '\r') Line.append((char) B);
            }
            return Line.toString();
        }

        /** Reassemble data frames into messages, answer pings, and report a close as {@link #Closed}. */
        private void ReadFrames() {
            ByteArrayOutputStream Message = new ByteArrayOutputStream();
            try {
                while (true) {
                    int First = ReadByte(), Second = ReadByte();
                    boolean Final = (First & 0x80) != 0;
                    int Opcode = First & 0x0f;
                    byte[] Payload = ReadPayload(Second);
                    if (Opcode == OpClose) break;
                    if (Opcode == OpPing) { SendFrame(OpPong, Payload); continue; }
                    if (Opcode == OpPong) continue;
                    if (Opcode == OpText || Opcode == OpBinary || Opcode == OpContinuation) Message.write(Payload);
                    if (Final) {
                        Inbox.add(Message.toString(StandardCharsets.UTF_8));
                        Message.reset();
                    }
                }
            } catch (IOException Ended) {
                // A dropped socket ends the stream the same way a close frame does.
            }
            Inbox.add(Closed);
        }

        private byte[] ReadPayload(int Second) throws IOException {
            long Length = Second & 0x7f;
            if (Length == 126) Length = (ReadByte() << 8) | ReadByte();
            else if (Length == 127) { Length = 0; for (int I = 0; I < 8; I++) Length = (Length << 8) | ReadByte(); }
            byte[] Mask = (Second & 0x80) != 0 ? In.readNBytes(4) : null;
            byte[] Payload = In.readNBytes((int) Length);
            if (Payload.length < Length) throw new EOFException();
            if (Mask != null) for (int I = 0; I < Payload.length; I++) Payload[I] ^= Mask[I % 4];
            return Payload;
        }

        private int ReadByte() throws IOException {
            int B = In.read();
            if (B < 0) throw new EOFException();
            return B;
        }

        /** Client frames are always masked (RFC 6455 section 5.3). */
        private synchronized void SendFrame(int Opcode, byte[] Payload) throws IOException {
            ByteArrayOutputStream Frame = new ByteArrayOutputStream();
            Frame.write(0x80 | Opcode);
            if (Payload.length < 126) {
                Frame.write(0x80 | Payload.length);
            } else if (Payload.length <= 0xffff) {
                Frame.write(0x80 | 126);
                Frame.write(Payload.length >> 8);
                Frame.write(Payload.length);
            } else {
                Frame.write(0x80 | 127);
                for (int Shift = 56; Shift >= 0; Shift -= 8) Frame.write((int) ((long) Payload.length >> Shift));
            }
            byte[] Mask = new byte[4];
            Random.nextBytes(Mask);
            Frame.write(Mask);
            for (int I = 0; I < Payload.length; I++) Frame.write(Payload[I] ^ Mask[I % 4]);
            Out.write(Frame.toByteArray());
            Out.flush();
        }

        void Send(String Text) throws IOException {
            SendFrame(OpText, Text.getBytes(StandardCharsets.UTF_8));
        }

        String Next() throws InterruptedException {
            return Inbox.take();
        }

        /** Send a JSON-RPC request and wait for the response with the same id. */
        Map<?, ?> Request(int Id, String Method, Map<String, Object> Params) throws Exception {
            Send(Json.Write(Json.Object("jsonrpc", "2.0", "id", Id, "method", Method, "params", Params)));
            while (true) {
                String Message = Inbox.poll(ProbeTimeoutSeconds, TimeUnit.SECONDS);
                if (Message == null) throw new IllegalStateException("timed out waiting for " + Method);
                if (Closed.equals(Message)) throw new IllegalStateException("connection closed during " + Method);
                Map<?, ?> Response = (Map<?, ?>) Json.Parse(Message);
                if (!Json.SameNumber(Response.get("id"), Id)) continue;
                if (Response.containsKey("error")) throw new IllegalStateException(Json.Write(Response.get("error")));
                return (Map<?, ?>) Response.get("result");
            }
        }

        /** A normal-closure frame (status 1000), then the socket. */
        void Close() {
            try {
                SendFrame(OpClose, new byte[] {0x03, (byte) 0xe8});
                Socket.close();
            } catch (IOException AlreadyGone) {
                // Nothing left to close.
            }
        }
    }

    /**
     * Just enough JSON: objects keep key order, numbers keep their original text, and output uses
     * ASCII escapes with ", " and ": " separators, as the forwarded messages always have.
     */
    static final class Json {

        record Number(String Text) {}

        static Map<String, Object> Object(Object... KeyValues) {
            Map<String, Object> Map = new LinkedHashMap<>();
            for (int I = 0; I < KeyValues.length; I += 2) Map.put((String) KeyValues[I], KeyValues[I + 1]);
            return Map;
        }

        static boolean SameNumber(Object Value, int Expected) {
            return Value instanceof Number N && N.Text().equals(Integer.toString(Expected));
        }

        static Object Parse(String Text) {
            Parser Reader = new Parser(Text);
            Object Value = Reader.ReadValue();
            Reader.SkipSpace();
            if (!Reader.AtEnd()) throw new IllegalArgumentException("trailing data in JSON");
            return Value;
        }

        static String Write(Object Value) {
            StringBuilder Out = new StringBuilder();
            WriteValue(Value, Out);
            return Out.toString();
        }

        private static void WriteValue(Object Value, StringBuilder Out) {
            if (Value == null) Out.append("null");
            else if (Value instanceof String S) WriteString(S, Out);
            else if (Value instanceof Number N) Out.append(N.Text());
            else if (Value instanceof Integer || Value instanceof Boolean) Out.append(Value);
            else if (Value instanceof Map<?, ?> Map) WriteObject(Map, Out);
            else if (Value instanceof List<?> List) WriteArray(List, Out);
            else throw new IllegalArgumentException("not JSON: " + Value.getClass());
        }

        private static void WriteObject(Map<?, ?> Map, StringBuilder Out) {
            Out.append('{');
            String Separator = "";
            for (Map.Entry<?, ?> Entry : Map.entrySet()) {
                Out.append(Separator);
                WriteString((String) Entry.getKey(), Out);
                Out.append(": ");
                WriteValue(Entry.getValue(), Out);
                Separator = ", ";
            }
            Out.append('}');
        }

        private static void WriteArray(List<?> List, StringBuilder Out) {
            Out.append('[');
            for (int I = 0; I < List.size(); I++) {
                if (I > 0) Out.append(", ");
                WriteValue(List.get(I), Out);
            }
            Out.append(']');
        }

        private static void WriteString(String S, StringBuilder Out) {
            Out.append('"');
            for (char C : S.toCharArray()) {
                switch (C) {
                    case '"' -> Out.append("\\\"");
                    case '\\' -> Out.append("\\\\");
                    case '\n' -> Out.append("\\n");
                    case '\r' -> Out.append("\\r");
                    case '\t' -> Out.append("\\t");
                    case '\b' -> Out.append("\\b");
                    case '\f' -> Out.append("\\f");
                    default -> {
                        if (C < 0x20 || C > 0x7e) Out.append(String.format("\\u%04x", (int) C));
                        else Out.append(C);
                    }
                }
            }
            Out.append('"');
        }

        private static final class Parser {
            private final String Text;
            private int At;

            Parser(String Text) { this.Text = Text; }

            boolean AtEnd() { return At >= Text.length(); }

            void SkipSpace() {
                while (!AtEnd() && Character.isWhitespace(Text.charAt(At))) At++;
            }

            Object ReadValue() {
                SkipSpace();
                if (AtEnd()) throw new IllegalArgumentException("unexpected end of JSON");
                char C = Text.charAt(At);
                if (C == '{') return ReadObject();
                if (C == '[') return ReadArray();
                if (C == '"') return ReadString();
                if (Text.startsWith("true", At)) { At += 4; return Boolean.TRUE; }
                if (Text.startsWith("false", At)) { At += 5; return Boolean.FALSE; }
                if (Text.startsWith("null", At)) { At += 4; return null; }
                return ReadNumber();
            }

            private Map<String, Object> ReadObject() {
                Map<String, Object> Map = new LinkedHashMap<>();
                Expect('{');
                SkipSpace();
                if (TryConsume('}')) return Map;
                do {
                    SkipSpace();
                    String Key = ReadString();
                    SkipSpace();
                    Expect(':');
                    Map.put(Key, ReadValue());
                    SkipSpace();
                } while (TryConsume(','));
                Expect('}');
                return Map;
            }

            private List<Object> ReadArray() {
                List<Object> List = new ArrayList<>();
                Expect('[');
                SkipSpace();
                if (TryConsume(']')) return List;
                do {
                    List.add(ReadValue());
                    SkipSpace();
                } while (TryConsume(','));
                Expect(']');
                return List;
            }

            private String ReadString() {
                Expect('"');
                StringBuilder Out = new StringBuilder();
                while (true) {
                    if (AtEnd()) throw new IllegalArgumentException("unterminated JSON string");
                    char C = Text.charAt(At++);
                    if (C == '"') return Out.toString();
                    if (C != '\\') { Out.append(C); continue; }
                    char E = Text.charAt(At++);
                    switch (E) {
                        case 'n' -> Out.append('\n');
                        case 'r' -> Out.append('\r');
                        case 't' -> Out.append('\t');
                        case 'b' -> Out.append('\b');
                        case 'f' -> Out.append('\f');
                        case 'u' -> { Out.append((char) Integer.parseInt(Text.substring(At, At + 4), 16)); At += 4; }
                        default -> Out.append(E);
                    }
                }
            }

            private Number ReadNumber() {
                int Start = At;
                while (!AtEnd() && "+-0123456789.eE".indexOf(Text.charAt(At)) >= 0) At++;
                if (Start == At) throw new IllegalArgumentException("invalid JSON at " + At);
                String Lexeme = Text.substring(Start, At);
                boolean IsInteger = Lexeme.chars().noneMatch(C -> C == '.' || C == 'e' || C == 'E');
                return new Number(IsInteger ? new BigInteger(Lexeme).toString()
                                            : PythonRepr(Double.parseDouble(Lexeme)));
            }

            /** Python's repr of a float: shortest round-trip digits, scientific outside 1e-4..1e16. */
            private static String PythonRepr(double Value) {
                if (Double.isInfinite(Value)) return Value > 0 ? "Infinity" : "-Infinity";
                if (Double.isNaN(Value)) return "NaN";
                if (Value == 0) return (1 / Value < 0) ? "-0.0" : "0.0";
                BigDecimal Exact = new BigDecimal(Double.toString(Math.abs(Value))).stripTrailingZeros();
                String Digits = Exact.unscaledValue().toString();
                int Exponent = Digits.length() - 1 - Exact.scale();
                String Sign = Value < 0 ? "-" : "";
                if (Exponent >= -4 && Exponent < 16) {
                    String Fixed = Exact.toPlainString();
                    return Sign + (Fixed.contains(".") ? Fixed : Fixed + ".0");
                }
                String Mantissa = Digits.length() == 1 ? Digits : Digits.charAt(0) + "." + Digits.substring(1);
                return Sign + Mantissa + "e" + (Exponent < 0 ? "-" : "+") + String.format("%02d", Math.abs(Exponent));
            }

            private boolean TryConsume(char C) {
                if (!AtEnd() && Text.charAt(At) == C) { At++; return true; }
                return false;
            }

            private void Expect(char C) {
                if (!TryConsume(C)) throw new IllegalArgumentException("expected '" + C + "' at " + At);
            }
        }
    }
}
