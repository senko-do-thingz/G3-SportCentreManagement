# Thong bao nhom - Thay doi sap toi cua Sportify (Flow 1-3)

Mình đã chốt một đợt bổ sung nghiệp vụ Sportify để chuẩn bị demo và vấn đáp hội đồng. Trúc sẽ phụ trách triển khai cùng Claude, chia thành 8 task nhỏ, mỗi task 1 PR và có ảnh chụp bằng chứng giao diện desktop thực tế. CODEOWNER sẽ review và quyết định merge, không tự merge.

- **Member Profile:** Tiếp tục hoàn thiện cập nhật thông tin cá nhân, ngày sinh, mục tiêu tập luyện và đổi mật khẩu; bổ sung bộ câu hỏi sức khỏe có sẵn để đưa ra lưu ý tập luyện (không phải chẩn đoán y tế).
- **Sportify Wallet:** Member được nạp tiền bằng QR, Receptionist xác nhận, sau đó dùng số dư nội bộ để thanh toán các dịch vụ đủ điều kiện; có sổ cái và chống cộng/trừ tiền hai lần.
- **Booking / Refund:** Khi lớp đã đầy, hệ thống phải xử lý cạnh tranh đặt chỗ an toàn. Nếu chưa bị trừ tiền thì không hoàn tiền ảo; nếu đã bị trừ một khoản liên quan đến Booking lỗi thì hoàn đúng khoản đó vào ví một lần. Booking sử dụng lượt trong gói thì vẫn giữ quy tắc hoàn lượt, không tự tạo tiền mới.
- **Withdrawal:** Member có thể lưu tài khoản ngân hàng, tạo yêu cầu rút số dư đủ điều kiện; Receptionist phê duyệt hoặc từ chối. Xác nhận phê duyệt khác với xác nhận đã chuyển tiền.
- **Receptionist Inbox:** Có hòm thư thông báo trong hệ thống khi phát sinh việc cần xử lý như nạp tiền, rút tiền, ngoại lệ thanh toán và yêu cầu hỗ trợ.
- **Member History:** Member xem được cả lịch sử thanh toán (Cash/Bank/QR) lẫn lịch sử ví (nạp, sử dụng, hoàn tiền, yêu cầu rút), trạng thái và chi tiết giao dịch của chính mình.

Phạm vi vẫn là **Desktop trên localhost**, một database `sportify`, không làm Mobile UI, không tích hợp ngân hàng tự động. Mục tiêu là đầy đủ logic nghiệp vụ để demo, không biến đây thành sản phẩm ví điện tử thương mại. Các PR chỉ được coi là sẵn sàng khi có test và screenshot desktop thực tế.
