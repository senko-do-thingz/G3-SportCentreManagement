# Báo cáo lỗi nghiệp vụ - Kiểm thử E2E hệ thống Sportify

> Hệ thống quản lý trung tâm thể thao Sportify, gồm backend Spring Boot + SQL Server và frontend React.
> Ngày kiểm thử: 05/11/2026. Phiên bản: nhánh `main`, commit [`c6c88c5b`](https://github.com/senko-do-thingz/G3-SportCentreManagement/commit/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83).
> Các link tới code trỏ đúng commit đã kiểm thử, nên vẫn mở đúng dòng kể cả khi `main` thay đổi.

## 1. Cách kiểm thử

- Chạy backend thật (profile `dev,demo`) trên một database SQL Server tạo mới riêng cho đợt test.
- Gọi HTTP API thật bằng tài khoản của cả 4 vai trò: Manager, Lễ tân (Receptionist), Huấn luyện viên (Coach), Hội viên (Member). Ngoài ra test có tạo thêm các hội viên mới.
- Sau mỗi bước, đối chiếu dữ liệu trong DB: booking, số buổi còn lại của gói, số ghế đã đặt, thanh toán, hoá đơn, điểm danh.
- Các tình huống "buổi tập đang diễn ra" và "buổi tập đã kết thúc" được mô phỏng bằng cách dời giờ buổi tập trong DB test sau khi đã đặt chỗ.

Hệ thống có 6 flow nghiệp vụ:

| Flow | Nội dung |
|---|---|
| 1 | Quản lý người dùng và hội viên (đăng ký, hồ sơ, mật khẩu, thẻ thành viên, gói tập, check-in tại quầy) |
| 2 | Đặt lịch và quản lý lịch tập |
| 3 | Thanh toán, hoàn tiền, báo cáo |
| 4 | Huấn luyện và điểm danh |
| 5 | Gợi ý bài tập cho Coach |
| 6 | Trợ lý tư vấn cho hội viên |

## 2. Kết quả tổng quan

| Flow | Số kịch bản | Đạt | Lỗi |
|---|---:|---:|---:|
| 1 | 19 | 15 | 4 |
| 2 | 18 | 17 | 1 |
| 3 | 16 | 16 | 0 |
| 4 | 16 | 12 | 4 |
| 5 | 4 | 4 | 0 |
| 6 | 8 | 8 | 0 |
| **Tổng** | **81** | **72** | **9** |

9 kịch bản lỗi gộp lại thành **7 lỗi nghiệp vụ**: 2 High, 3 Medium, 2 Low.

| Mã lỗi | Mức độ | Flow | Tóm tắt |
|---|---|---|---|
| E2E-BUG-01 |High | 2 | Hội viên đặt được hai buổi tập trùng giờ |
| E2E-BUG-02 |High | 4 (ảnh hưởng 2) | Đã điểm danh "có mặt" vẫn huỷ được booking và được hoàn buổi |
| E2E-BUG-03 |Medium | 1 (ảnh hưởng 4) | Check-in tại quầy vẫn được chấp nhận khi buổi tập đã kết thúc |
| E2E-BUG-04 |Medium | 1 | Lễ tân bán được gói tập và thẻ cho tài khoản đã bị vô hiệu hoá |
| E2E-BUG-05 |Medium | 1 | Token đăng nhập cũ vẫn dùng được sau khi đổi mật khẩu |
| E2E-BUG-06 |Low | 1 (ảnh hưởng 3) | Hội viên tạo được nhiều đơn chờ thanh toán cho cùng một gói |
| E2E-BUG-07 |Low | 4 | Điểm danh "có mặt" được trước giờ buổi tập bắt đầu |

Thang mức độ:
- **High:** sai nghiệp vụ, gây thiệt hại tiền hoặc tài nguyên, và dễ xảy ra.
- **Medium:** sai nghiệp vụ hoặc ảnh hưởng bảo mật, nhưng cần điều kiện cụ thể mới xảy ra.
- **Low:** bất nhất hoặc làm tăng rủi ro, ít gây hậu quả trực tiếp.

---

## 3. Chi tiết từng lỗi

### E2E-BUG-01 - Hội viên đặt được hai buổi tập trùng giờ

| | |
|---|---|
| **Mức độ** |High |
| **Flow** | 2 - Đặt lịch và quản lý lịch tập |

**Mô tả.** Một hội viên có cả gói Cầu lông và gói Bóng rổ (đều có huấn luyện viên). Hội viên này đặt thành công cả hai buổi diễn ra cùng ngày 30/11/2026, cùng khung giờ 14:00-15:30, ở hai lớp và hai sân khác nhau. Cả hai booking đều ở trạng thái `CONFIRMED`.

**Các bước tái hiện.**
1. Manager tạo hai buổi tập cùng ngày và cùng giờ ở hai lớp khác nhau.
2. Một hội viên có gói hợp lệ cho cả hai môn đặt buổi thứ nhất: thành công (`201`).
3. Hội viên đó đặt tiếp buổi thứ hai: vẫn thành công (`201`). Kết quả mong đợi là bị từ chối.

**Hậu quả.** Hội viên bị trừ 2 buổi và giữ 2 chỗ nhưng chỉ đi được một buổi, nên một chỗ luôn bị bỏ trống và người khác mất cơ hội đặt. Lịch cá nhân của hội viên cũng bị chồng chéo.

**Nguyên nhân.** Hàm tạo booking ([`BookingServiceImpl.createBooking`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java#L105-L127)) chỉ kiểm tra trùng booking trong cùng một buổi. Hệ thống đã kiểm tra trùng lịch cho Coach và cho sân, nhưng chưa kiểm tra cho hội viên.

**Gợi ý sửa.**
1. Thêm truy vấn kiểm tra xem hội viên đã có booking `CONFIRMED` nào khác trong cùng ngày với khung giờ giao nhau hay chưa (điều kiện `start < end_khác AND end > start_khác`), bỏ qua các buổi đã huỷ.
2. Chạy kiểm tra này sau bước khoá buổi tập và trước bước giữ chỗ. Nếu trùng thì trả `409` với mã `TIME_CONFLICT` kèm thông tin buổi bị trùng.
3. Viết thêm integration test cho trường hợp hai buổi cùng giờ ở hai lớp khác nhau.

---

### E2E-BUG-02 - Đã điểm danh "có mặt" vẫn huỷ được booking và được hoàn buổi

| | |
|---|---|
| **Mức độ** |High |
| **Flow** | 4 - Huấn luyện và điểm danh (ảnh hưởng Flow 2: huỷ booking) |

**Mô tả.** Lỗi tái hiện được theo hai cách:
- **Lễ tân huỷ.** Buổi bơi đang diễn ra và Coach đã điểm danh hội viên là `PRESENT`. Lễ tân huỷ booking thành công. Gói của hội viên được cộng lại 1 buổi (7 -> 8), trong khi điểm danh vẫn ghi `PRESENT`.
- **Hội viên tự huỷ.** Coach điểm danh `PRESENT` cho buổi trong ngày nhưng chưa bắt đầu (xem thêm E2E-BUG-07). Lúc đó còn hơn 120 phút trước giờ bắt đầu, nên hội viên tự huỷ được và cũng được hoàn buổi (7 -> 8).

**Hậu quả.** Hội viên được ghi nhận là đã tập nhưng không bị trừ buổi, tức là tập miễn phí. Báo cáo điểm danh vẫn đếm một lượt "có mặt" gắn với một booking đã huỷ, nên số liệu giữa các báo cáo không khớp nhau.

**Nguyên nhân.** Hàm huỷ booking ([`BookingServiceImpl.cancelBooking`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/impl/BookingServiceImpl.java#L196-L215)) chỉ chặn khi booking đã check-in thành công tại quầy, mà không xét đến bản ghi điểm danh. Lễ tân được phép huỷ cho đến khi buổi tập kết thúc.

**Gợi ý sửa.**
1. Không cho huỷ booking đã có điểm danh `PRESENT` hoặc `LATE`. Trả lỗi `ALREADY_ATTENDED` với thông điệp "Đã điểm danh, hãy chỉnh sửa điểm danh trước khi huỷ".
2. Nếu nghiệp vụ vẫn cần huỷ sau khi điểm danh (do điểm danh nhầm), bắt buộc đi qua luồng chỉnh sửa điểm danh có lý do, và không tự động hoàn buổi.
3. Viết test cho cả hai vai trò: hội viên huỷ và lễ tân huỷ.

---

### E2E-BUG-03 - Check-in tại quầy vẫn được chấp nhận khi buổi tập đã kết thúc

| | |
|---|---|
| **Mức độ** |Medium |
| **Flow** | 1 - Check-in tại quầy lễ tân (ảnh hưởng Flow 4) |

**Mô tả.** Buổi tập hôm nay diễn ra 08:00-09:00. Lúc 21:30, lễ tân check-in cho booking này và hệ thống trả kết quả `ALLOWED`.

**Hậu quả.**
- Hệ thống ghi nhận "đã đến" cho một buổi đã qua.
- Booking đã check-in thì không huỷ được nữa, nên lượt check-in muộn này còn khoá luôn booking.
- Số liệu "đã đến" trong báo cáo vận hành bị sai.

**Nguyên nhân.** Hàm check-in ([`CheckInServiceImpl`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/impl/CheckInServiceImpl.java#L108-L125)) chỉ kiểm tra ngày của buổi tập có phải hôm nay hay không, không kiểm tra giờ bắt đầu và giờ kết thúc.

**Gợi ý sửa.**
1. Thêm cửa sổ check-in cấu hình được, ví dụ từ 60 phút trước giờ bắt đầu đến giờ kết thúc. Ngoài khoảng này, trả kết quả `DENIED` với lý do "Ngoài thời gian check-in".
2. Khi không chỉ định booking cụ thể, chỉ chọn những booking còn trong cửa sổ check-in.
3. Viết test cho cả hai phía: check-in quá sớm và check-in sau khi buổi đã kết thúc.

---

### E2E-BUG-04 - Lễ tân bán được gói tập và thẻ cho tài khoản đã bị vô hiệu hoá

| | |
|---|---|
| **Mức độ** |Medium |
| **Flow** | 1 - Quản lý hội viên, bán tại quầy (liên quan Flow 3) |

**Mô tả.** Manager chuyển một hội viên sang trạng thái `INACTIVE`, và hội viên này đúng là không đăng nhập được nữa. Tuy vậy, lễ tân vẫn:
- bán được gói tập cho hội viên đó: thành công, gói ở trạng thái `ACTIVE`, thanh toán `SUCCESS`, có hoá đơn;
- bán được thẻ GOLD: thành công, thẻ ở trạng thái `ACTIVE`.

**Hậu quả.** Trung tâm thu tiền của một tài khoản không thể dùng dịch vụ, sau đó phải hoàn tiền thủ công. Doanh thu bị ghi nhận sai.

**Nguyên nhân.** Hàm bán gói ([`SportPackageServiceImpl.registerPackage`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/impl/SportPackageServiceImpl.java#L185-L201)) và hàm bán thẻ ([`MembershipCardServiceImpl.purchaseCard`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java#L258-L290)) chỉ kiểm tra vai trò MEMBER, không kiểm tra trạng thái tài khoản. Trong khi đó, đặt lịch và check-in đều đã kiểm tra trạng thái này.

**Gợi ý sửa.** Khi nhân viên bán gói hoặc thẻ, kiểm tra tài khoản của hội viên phải đang `ACTIVE`. Nếu không thì trả lỗi `MEMBER_INACTIVE`, và màn hình quầy hiển thị thông báo rõ ràng cho lễ tân.

---

### E2E-BUG-05 - Token đăng nhập cũ vẫn dùng được sau khi đổi mật khẩu

| | |
|---|---|
| **Mức độ** |Medium (bảo mật tài khoản) |
| **Flow** | 1 - Hồ sơ người dùng, đổi mật khẩu |

**Mô tả.** Hội viên đổi mật khẩu thành công, và mật khẩu cũ không đăng nhập được nữa. Nhưng access token được cấp trước khi đổi vẫn gọi API thành công (`200`).

**Hậu quả.** Người dùng thường đổi mật khẩu khi nghi bị lộ tài khoản. Trong trường hợp đó, người đang giữ token cũ vẫn tiếp tục truy cập được cho đến khi token hết hạn.

**Nguyên nhân.** Bộ lọc JWT ([`JwtAuthenticationFilter`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/identity/security/JwtAuthenticationFilter.java#L52)) chỉ kiểm tra chữ ký, thời hạn và trạng thái tài khoản. Hệ thống không có cơ chế thu hồi token khi đổi mật khẩu.

**Gợi ý sửa.**
1. Thêm cột `password_changed_at` (hoặc `token_version`) vào bảng người dùng bằng một migration mới.
2. Cập nhật cột này mỗi khi đổi mật khẩu.
3. Từ chối mọi token có thời điểm cấp (`iat`) sớm hơn `password_changed_at`, áp dụng cho cả access token và refresh token.

---

### E2E-BUG-06 - Hội viên tạo được nhiều đơn chờ thanh toán cho cùng một gói

| | |
|---|---|
| **Mức độ** |Low |
| **Flow** | 1 - Hội viên mua gói online (ảnh hưởng hàng đợi thanh toán của Flow 3) |

**Mô tả.** Hội viên gửi yêu cầu mua cùng một gói hai lần liên tiếp. Cả hai lần đều thành công, tạo ra 2 đơn `PENDING_PAYMENT` và 2 khoản thanh toán chờ. Với thẻ thành viên, hệ thống đã chặn trường hợp này, nên hai luồng đang xử lý không nhất quán.

**Hậu quả.**
- Hàng đợi xác nhận thanh toán của lễ tân có đơn trùng.
- Hội viên có thể bị thu tiền hai lần chỉ vì bấm hai lần.
- Báo cáo "chờ thanh toán" bị phình lên.

**Gợi ý sửa.** Khi hội viên mua online, nếu đã có đơn `PENDING_PAYMENT` cho cùng gói thì trả `409 PENDING_ORDER_EXISTS` kèm mã đơn cũ, hoặc trả lại chính đơn cũ. Có thể làm theo cách [luồng mua thẻ đang xử lý](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/impl/MembershipCardServiceImpl.java#L307-L310).

---

### E2E-BUG-07 - Điểm danh "có mặt" được trước giờ buổi tập bắt đầu

| | |
|---|---|
| **Mức độ** |Low (riêng lẻ), nhưng là điều kiện để xảy ra E2E-BUG-02 |
| **Flow** | 4 - Huấn luyện và điểm danh |

**Mô tả.** Buổi tập bắt đầu lúc 22:40, nhưng lúc 21:30 Coach đã điểm danh hội viên là `PRESENT` thành công.

**Nguyên nhân.** Hàm điểm danh ([`TrainingWriteService.requireAttendanceDate`](https://github.com/senko-do-thingz/G3-SportCentreManagement/blob/c6c88c5bbb65b25dfaa9c4d43d9181c14abb9a83/backend/src/main/java/com/sportify/catalog/service/TrainingWriteService.java#L349-L356)) chỉ so sánh theo ngày, không so sánh giờ. Comment trong code cho thấy đây là thiết kế "linh hoạt" có chủ ý, nhưng khi kết hợp với E2E-BUG-02 thì nó tạo ra lỗ hổng hoàn buổi.

**Gợi ý sửa.** Chỉ cho phép `PRESENT` hoặc `LATE` từ khoảng 15 phút trước giờ bắt đầu. Vẫn cho phép đánh `EXCUSED` (vắng có phép) trước giờ. Đây là quyết định nghiệp vụ: nếu nhóm giữ cách làm linh hoạt hiện tại thì bắt buộc phải sửa E2E-BUG-02.

---

## 4. Những phần hoạt động đúng

- **Flow 1:**
  - Đăng ký: chặn email trùng (kể cả khác chữ hoa/thường) và mật khẩu yếu.
  - Hồ sơ: chặn ngày sinh ở tương lai.
  - Đổi mật khẩu: kiểm tra mật khẩu cũ và mật khẩu xác nhận.
  - Tài khoản bị vô hiệu hoá không đăng nhập được.
  - Thẻ GOLD giảm đúng 5%.
  - Lễ tân không tạo được hội viên trùng số điện thoại hoặc email.
  - Phân quyền giữa các vai trò đúng.
- **Flow 2:**
  - Chặn đặt khi buổi đã đủ chỗ, đặt trùng, đặt sai môn, sai hình thức tập, hoặc trước ngày gói có hiệu lực.
  - Huỷ booking hoàn đúng buổi và trả lại ghế.
  - Nhiều người đặt cùng lúc vào chỗ cuối cùng: chỉ một người thành công.
  - Manager không tạo được lịch trùng Coach hoặc sân, lịch trong quá khứ, hoặc giờ kết thúc trước giờ bắt đầu.
  - Huỷ buổi tập thì huỷ luôn các booking, hoàn buổi và gửi thông báo cho hội viên.
- **Flow 3:**
  - Chặn xác nhận thanh toán thiếu tiền hoặc xác nhận hai lần. Mỗi thanh toán có đúng một hoá đơn.
  - Không xem được thanh toán hay hoá đơn của người khác.
  - Hoàn tiền:
    - mỗi thanh toán chỉ có một yêu cầu hoàn đang mở;
    - không hoàn vượt số tiền đã trả;
    - hội viên và lễ tân không tự duyệt được;
    - khi hoàn tất, thanh toán chuyển sang REFUNDED và các booking sắp tới bị huỷ;
    - báo cáo tài chính cập nhật đúng.
- **Flow 4:**
  - Coach không điểm danh hay xem học viên của lớp mình không phụ trách.
  - Chặn điểm danh cho ngày tương lai và chặn nhập kết quả trước khi buổi kết thúc.
  - Hội viên vắng mặt không nhận được kết quả.
  - Chỉnh sửa điểm danh bắt buộc có lý do.
  - Mỗi booking chỉ check-in được một lần.
- **Flow 5:**
  - Gợi ý bài tập chỉ là bản nháp, không lưu vào DB.
  - Coach khác hoặc hội viên không xem được.
  - Gán giáo án trùng bị chặn.
- **Flow 6:**
  - Hội viên phải đồng ý (opt-in) mới dùng được. Câu hỏi giới hạn 500 ký tự. Chỉ hội viên được dùng.
  - Không tiết lộ thông tin của hội viên khác.
  - Câu hỏi y tế được chuyển cho Coach.
  - Trợ lý không tự tạo booking hay thanh toán.

## 5. Giới hạn của đợt kiểm thử

- Mới kiểm thử ở tầng API + Database, chưa chạy kịch bản qua giao diện web.
- Chưa kiểm thử refresh token sau khi đổi mật khẩu, thanh toán QR/VietQR (để không gọi API ngân hàng thật), và các job tự động hết hạn gói.
- Các tình huống phụ thuộc thời gian được mô phỏng bằng cách dời giờ buổi tập trong DB test.

## 6. Kiểm chứng trên GitHub CI

Mỗi lỗi có ít nhất một integration test trong [`E2eBusinessDefectsIntegrationTest`](../../backend/src/test/java/com/sportify/catalog/service/E2eBusinessDefectsIntegrationTest.java).

- Test chạy trên SQL Server 2022 (Testcontainers), cùng job "Integration tests" của CI.
- Đồng hồ được cố định ở thứ Tư 10/03/2027, 10:00 giờ Việt Nam, nên các quy tắc phụ thuộc thời gian luôn cho cùng một kết quả.
- Test kiểm tra hành vi ĐÚNG, nên sẽ báo đỏ cho tới khi lỗi được sửa. Sửa xong lỗi nào thì test của lỗi đó tự chuyển xanh.

| Mã lỗi | Test | Kết quả trên `main` hiện tại |
|---|---|---|
| E2E-BUG-01 | `memberCannotBookTwoOverlappingSessions` | Booking thứ hai cùng giờ vẫn thành công |
| E2E-BUG-02 | `presentBookingCannotBeCancelledForCredit` | Huỷ booking đã PRESENT vẫn thành công |
| E2E-BUG-03 | `checkInDeniedAfterSessionEnded` | expected `DENIED`, but was `ALLOWED` |
| E2E-BUG-04 | `deskPackageSaleRejectedForInactiveMember`, `deskCardSaleRejectedForInactiveMember` | Bán gói và bán thẻ vẫn thành công |
| E2E-BUG-05 | `accessTokenRevokedAfterPasswordChange` | expected `401`, but was `200` |
| E2E-BUG-06 | `secondPendingOrderOfSamePackageRejected` | Đơn chờ thanh toán thứ hai vẫn được tạo |
| E2E-BUG-07 | `presentCannotBeRecordedBeforeStart` | Điểm danh trước giờ vẫn thành công |

Chạy lại trên máy (cần Docker, hoặc đặt `UNIFY_TEST_SQL_PORT`, `UNIFY_TEST_DB_USER`, `UNIFY_TEST_DB_PASSWORD`, và `JWT_SECRET`):

```bash
cd backend
mvn verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=E2eBusinessDefectsIntegrationTest
```

Kết quả CI đầu tiên trên PR [#224](https://github.com/senko-do-thingz/G3-SportCentreManagement/pull/224), run [42050853981](https://github.com/senko-do-thingz/G3-SportCentreManagement/actions/runs/42050853981): job Integration chạy 1058 test, trong đó **đúng 8 test fail và đều là test của báo cáo này**. Toàn bộ 1050 test còn lại pass. Các job unit test, CodeQL, security và kiểm tra convention đều pass.
