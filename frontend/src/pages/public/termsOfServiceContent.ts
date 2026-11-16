/** Cau truc mot muc dieu khoan trong accordion */
export interface TosSection {
  id: string
  title: string
  content: string[]
}

/** 9 muc dieu khoan dich vu theo quy chuan tai lieu ban giao */
export const TOS_SECTIONS: TosSection[] = [
  {
    id: 'general-provisions',
    title: '1. General Provisions',
    content: [
      'These Terms of Service govern your access to and use of Sportify Sports Center facilities, services, website, mobile interfaces, and booking systems. By registering an account, purchasing any package, or entering our center facilities, you agree to comply with and be bound by these terms.',
      'Sportify operates multi-sport training facilities including swimming pools, badminton courts, tennis courts, basketball courts, volleyball courts, and football fields. These facilities are maintained for member fitness, training classes, and recreational sport activities.',
      'Users must be at least 16 years of age to register an independent account. Minors under 16 years of age must have written parental or legal guardian consent to participate in center activities.',
    ],
  },
  {
    id: 'account-responsibilities',
    title: '2. Account and User Responsibilities',
    content: [
      'Users agree to provide accurate, current, and complete information during registration, including full name, phone number, and valid email address. Providing fraudulent or misleading information is strictly prohibited.',
      'Each member account is strictly personal and non-transferable. You are responsible for safeguarding your login credentials and for all activities that occur under your account.',
      'You must notify Sportify staff immediately upon discovering any unauthorized use of your account, security breach, or loss of access credentials. Sportify is not liable for losses resulting from shared or compromised credentials.',
    ],
  },
  {
    id: 'packages-and-payments',
    title: '3. Membership Packages and Payments',
    content: [
      'Sportify provides flexible training options including Single Visit passes, Coach-led class packages, Self-training packages, and tiered Membership Cards (Silver, Gold, Platinum).',
      'Package pricing, validity duration, and session quotas are transparently published on our website and at the front desk. Prices are denominated in Vietnamese Dong (VND) and include applicable taxes unless explicitly noted.',
      'Accepted payment methods include secure VietQR bank transfers, direct online banking, and counter cash/card settlement at the Reception Desk. Transactions are confirmed once verified by our payment gateway or reception staff.',
      'Packages are activated on the selected start date or upon the first completed session. Packages naturally expire at the end of their stated duration or when session quotas are exhausted, whichever occurs first.',
    ],
  },
  {
    id: 'booking-and-cancellation',
    title: '4. Booking and Cancellation',
    content: [
      'Session bookings for coach-led classes and court slots must be reserved through the Sportify member portal or with assistance from the Reception Desk prior to attendance.',
      'Check-in is required before entering training areas. Members must scan their personal Member QR code at the Reception scanner or present identification to the receptionist upon arrival.',
      'Class and court reservations may be cancelled without penalty up to 2 hours before the scheduled start time. Cancellations made within the allowable window return the session credit to the member account.',
      'Late cancellations or no-show absences without prior notification will result in forfeiture of the session credit. Refund requests must follow official management review and front-desk approval policies.',
    ],
  },
  {
    id: 'conduct-and-safety',
    title: '5. User Conduct and Safety',
    content: [
      'All members, visitors, and athletes must adhere to safety rules, wear appropriate athletic attire and non-marking indoor footwear, and follow the instructions of coaches and floor staff.',
      'Harassment, abusive language, violence, discrimination, intentional damage to equipment, or disruptive behavior will result in immediate expulsion and potential account termination without refund.',
      'Members are responsible for assessing their physical readiness before participating in sports activities. Sportify strongly encourages members to undergo medical assessments prior to strenuous exercise.',
      'Sportify provides lockers for personal convenience. However, members are advised not to bring high-value items into the center. Sportify is not liable for loss or theft of unattended personal belongings.',
    ],
  },
  {
    id: 'privacy-and-data',
    title: '6. Personal Data and Privacy',
    content: [
      'Sportify collects and processes member personal data strictly for service provisioning, authentication, attendance tracking, payment verification, and center communications.',
      'We apply industry-standard security practices, including data encryption in transit and secure role-based access control, to safeguard your personal information.',
      'We do not sell, rent, or distribute personal data to third parties. Data is shared with external parties only when necessary to process verified transactions (e.g., payment gateways) or when mandated by law.',
      'Members retain the right to review, update, or request correction of their profile details via the My Profile portal or by contacting the Receptionist Desk.',
    ],
  },
  {
    id: 'ai-services',
    title: '7. AI Services and Limitations',
    content: [
      'Sportify incorporates an AI Assistant (Spori) designed to support members with class discovery, schedule queries, center navigation, and general fitness guidance.',
      'Recommendations provided by Spori are informational and advisory only. Spori does not provide medical diagnoses, physical therapy recommendations, or emergency response services.',
      'Members should consult qualified coaches and healthcare professionals for personalized medical or advanced training plans.',
      'Sportify continuously optimizes AI capabilities, but does not guarantee continuous, uninterrupted availability of AI features during scheduled system maintenance or upgrades.',
    ],
  },
  {
    id: 'dispute-resolution',
    title: '8. Complaints and Dispute Resolution',
    content: [
      'If you encounter an issue with facility conditions, billing discrepancies, coaching quality, or front desk service, you are encouraged to submit a Support Request through the member portal or speak directly with the Receptionist Desk.',
      'Complaints that cannot be resolved at the desk will be escalated to Center Management for investigation. Management commits to reviewing and responding to formal inquiries within 3 business days.',
      'Disputes arising out of or in connection with these terms shall first be addressed through amicable negotiation. If a resolution cannot be reached, the dispute will be settled in accordance with Vietnamese law.',
    ],
  },
  {
    id: 'changes-and-effective-date',
    title: '9. Changes and Effective Date',
    content: [
      'Sportify reserves the right to modify, amend, or update these Terms of Service at any time to reflect operational, legal, or regulatory requirements.',
      'Material changes will be notified via website notices, email updates, or inbox notifications in the member dashboard prior to their effective date.',
      'Continued use of Sportify facilities, accounts, or services following the publication of revised terms constitutes full acceptance of the updated terms.',
      'These Terms of Service are effective as of November 17, 2026, and supersede all prior verbal or written understandings.',
    ],
  },
]
