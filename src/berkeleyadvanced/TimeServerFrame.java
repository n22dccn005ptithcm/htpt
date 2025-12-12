package berkeleyadvanced;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class TimeServerFrame extends JFrame {
    private JLabel lblClock;
    private JTable tblLog;
    private DefaultTableModel tableModel;
    private JButton btnStart;
    
    // Danh sách Client: Map socket với (Index cột trong bảng)
    // Cấu trúc bảng: [Time Global] | [Time M1] [Content M1] | [Time M2] [Content M2] ...
    private Map<Socket, Integer> clientColumnMap = new HashMap<>();
    private List<Socket> clients = new ArrayList<>();
    
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    private boolean isRunning = false;

    // Màu sắc quy định
    private final Color COL_REQUEST = new Color(204, 255, 204); // Xanh nhạt (Yêu cầu)
    private final Color COL_RECEIVE = new Color(255, 255, 204); // Vàng nhạt (Nhận)
    private final Color COL_ADJUST  = new Color(255, 204, 204); // Đỏ nhạt (Điều chỉnh)

    public TimeServerFrame() {
        initComponents();
        startSystemClock();
        startConnectionListener();
    }

    private void initComponents() {
        setTitle("SERVER - BERKELEY MASTER");
        setSize(1000, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Top Panel: Đồng hồ + Nút bắt đầu
        JPanel pnlTop = new JPanel(new FlowLayout());
        lblClock = new JLabel("00:00:00.000");
        lblClock.setFont(new Font("Consolas", Font.BOLD, 40));
        lblClock.setForeground(Color.BLUE);
        
        btnStart = new JButton("BẮT ĐẦU ĐỒNG BỘ");
        btnStart.setFont(new Font("Arial", Font.BOLD, 16));
        btnStart.addActionListener(e -> startSyncProcess());

        pnlTop.add(lblClock);
        pnlTop.add(Box.createHorizontalStrut(30));
        pnlTop.add(btnStart);
        add(pnlTop, BorderLayout.NORTH);

        // 2. Center: Bảng Log đa cột
        // Cột 0 cố định là thời gian server log
        tableModel = new DefaultTableModel();
        tableModel.addColumn("Server Log Time"); 

        tblLog = new JTable(tableModel);
        tblLog.setRowHeight(25);
        
        // Custom Renderer để tô màu
        tblLog.setDefaultRenderer(Object.class, new ColorLogRenderer());
        
        add(new JScrollPane(tblLog), BorderLayout.CENTER);
    }

    private void startSystemClock() {
        new javax.swing.Timer(100, e -> lblClock.setText(timeFormat.format(new Date()))).start();
    }

    private void startConnectionListener() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(9999)) {
                while (true) {
                    Socket client = serverSocket.accept();
                    clients.add(client);
                    
                    // Khi có Client mới -> Thêm 2 cột (Time Mi, Content Mi)
                    int clientId = clients.size();
                    String colPrefix = "M" + clientId;
                    
                    SwingUtilities.invokeLater(() -> {
                        tableModel.addColumn(colPrefix + " Time");
                        tableModel.addColumn(colPrefix + " Content");
                        // Map socket này với chỉ số cột bắt đầu (Cột 0 là Server Time, nên bắt đầu từ 1, 3, 5...)
                        // Client 1: cột 1,2. Client 2: cột 3,4...
                        clientColumnMap.put(client, 1 + (clientId - 1) * 2);
                        
                        // Thông báo ra bảng (dòng dummy)
                        Vector<Object> row = new Vector<>();
                        row.add(timeFormat.format(new Date()));
                        tableModel.insertRow(0, row);
                    });
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void startSyncProcess() {
        if (clients.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chưa có máy Client nào kết nối!");
            return;
        }
        if (isRunning) return;
        isRunning = true;
        btnStart.setEnabled(false);
        btnStart.setText("Đang chạy đồng bộ...");

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); // Chu kỳ 5 giây
                    performBerkeleySync();
                } catch (Exception e) {}
            }
        }).start();
    }

    // --- LOGIC BERKELEY VÀ CẬP NHẬT BẢNG ---
    private void performBerkeleySync() {
        // Tạo một dòng mới cho chu kỳ này
        Vector<Object> currentRow = new Vector<>();
        // Khởi tạo row full null để tránh lỗi index
        for(int i=0; i<tableModel.getColumnCount(); i++) currentRow.add(""); 
        currentRow.set(0, timeFormat.format(new Date())); // Cột Server Time
        
        // 1. XANH (Gửi Yêu cầu)
        long t1 = System.currentTimeMillis();
        for (Socket client : clients) {
            try {
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                out.writeUTF("REQ");
                
                int colIdx = clientColumnMap.get(client);
                currentRow.set(colIdx, timeFormat.format(new Date()));
                currentRow.set(colIdx + 1, "REQ: Yêu cầu Time");
            } catch (Exception e) {}
        }
        updateTable(currentRow, "REQ"); // Cập nhật bảng màu xanh

        // 2. VÀNG (Nhận Thời gian)
        List<Long> diffs = new ArrayList<>();
        long totalDiff = 0;
        
        // Clone row cũ để update tiếp vào dòng đó (hoặc tạo dòng mới nếu muốn log chạy dọc)
        // Ở đây ta tạo dòng mới cho mỗi bước để dễ nhìn theo yêu cầu "tuần tự"
        Vector<Object> receiveRow = new Vector<>();
        for(int i=0; i<tableModel.getColumnCount(); i++) receiveRow.add("");
        receiveRow.set(0, timeFormat.format(new Date()));

        for (Socket client : clients) {
            try {
                DataInputStream in = new DataInputStream(client.getInputStream());
                long tClientRaw = in.readLong();
                long t4 = System.currentTimeMillis();
                long rtt = t4 - t1;
                long tClientEst = tClientRaw + (rtt/2);
                
                long diff = tClientEst - System.currentTimeMillis();
                diffs.add(diff);
                totalDiff += diff;

                int colIdx = clientColumnMap.get(client);
                receiveRow.set(colIdx, timeFormat.format(new Date(tClientRaw))); // Giờ của Client
                receiveRow.set(colIdx + 1, "RECV: T_Client=" + tClientRaw);
            } catch (Exception e) {
                 diffs.add(0L); // Lỗi coi như không lệch
            }
        }
        updateTable(receiveRow, "RECV"); // Cập nhật bảng màu vàng

        // 3. ĐỎ (Gửi Điều chỉnh)
        long avgDiff = totalDiff / (clients.size() + 1); // +1 là server
        
        Vector<Object> adjustRow = new Vector<>();
        for(int i=0; i<tableModel.getColumnCount(); i++) adjustRow.add("");
        adjustRow.set(0, timeFormat.format(new Date()));

        int idx = 0;
        for (Socket client : clients) {
            try {
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                long adj = avgDiff - diffs.get(idx);
                out.writeUTF("ADJ");
                out.writeLong(adj);
                
                int colIdx = clientColumnMap.get(client);
                adjustRow.set(colIdx, timeFormat.format(new Date()));
                adjustRow.set(colIdx + 1, "ADJ: " + (adj>0?"+":"") + adj + "ms");
                idx++;
            } catch (Exception e) {}
        }
        updateTable(adjustRow, "ADJ"); // Cập nhật bảng màu đỏ
    }

    private void updateTable(Vector<Object> rowData, String type) {
        SwingUtilities.invokeLater(() -> {
            // Nhét type vào object cuối dòng (ẩn) hoặc dùng logic check string để tô màu
            // Ở đây ta dùng thủ thuật: Gắn tag vào string content để Renderer nhận biết
            for(int i=1; i<rowData.size(); i+=2) { // Duyệt các cột Content
                String s = (String)rowData.get(i);
                if(s != null && !s.isEmpty()) {
                    // Dữ liệu đã có prefix REQ, RECV, ADJ ở logic trên rồi
                }
            }
            tableModel.insertRow(0, rowData);
            if (tableModel.getRowCount() > 100) tableModel.setRowCount(100);
        });
    }

    // Class tô màu cho bảng
    class ColorLogRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            String content = "";
            // Logic check màu dựa vào nội dung của cột Content bên cạnh hoặc chính nó
            // Cấu trúc: [Time] [Content] -> Nếu render cột Time, check cột Content (col+1). Nếu render Content, check chính nó.
            
            int contentColIndex = (column % 2 == 0) ? column : column + 1; // Cột 0 là ServerTime, Cột 1 là Time M1, Cột 2 Content M1...
            // Logic cột bảng Server hơi lệch do cột 0. 
            // Cột 1 (Time M1) -> Check cột 2 (Content M1).
            
            if (column == 0) return c; // Cột Server Time để trắng
            
            // Lấy nội dung để quyết định màu
            try {
                int checkCol = (column % 2 != 0) ? column + 1 : column;
                Object val = table.getValueAt(row, checkCol);
                if (val != null) content = val.toString();
            } catch (Exception e) {}

            if (content.startsWith("REQ")) c.setBackground(COL_REQUEST);
            else if (content.startsWith("RECV")) c.setBackground(COL_RECEIVE);
            else if (content.startsWith("ADJ")) c.setBackground(COL_ADJUST);
            else c.setBackground(Color.WHITE);
            
            c.setForeground(Color.BLACK);
            return c;
        }
    }
}