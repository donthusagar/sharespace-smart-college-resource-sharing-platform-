📚 ShareSpace – Smart College Resource Sharing Platform

ShareSpace is a full-stack web application designed to help college students share academic resources such as textbooks, calculators, lab equipment, and other educational materials.

Instead of purchasing resources that are needed only for a short period, students can borrow, lend, exchange, and return resources through a single college-focused platform.

🚀 Project Overview

Students frequently need academic resources for short periods. Buying these items can be expensive, while unused resources remain with other students.

ShareSpace solves this problem by creating a centralized platform where students can share resources within their college community.

✨ Key Features

- 🔐 Student Authentication: Secure student registration and login.
- 📦 Resource Listing: Add resources with descriptions and images.
- 🔍 Search & Filters: Find resources using keywords and categories.
- 📩 Borrowing Requests: Send requests to borrow available resources.
- ✅ Approval System: Resource owners can approve or reject requests.
- 📅 Return Tracking: Track borrowing and expected return dates.
- ⭐ Ratings & Reviews: Students can rate their sharing experiences.
- 🛡️ Admin Moderation: Manage users, listings, and reported content.
- 🔔 Notifications: Receive updates about borrowing requests and approvals.

🛠️ Technology Stack

Technology| Purpose
React.js| Frontend development
Tailwind CSS| UI styling
Node.js| Backend runtime
Express.js| REST API
MongoDB| Database
Firebase Authentication| Student authentication
Cloudinary| Image storage
Firebase Cloud Messaging / Email| Notifications

🏗️ System Architecture

          Student Web Application
                    |
                    v
              Express REST API
                    |
          ---------------------
          |                   |
          v                   v
       MongoDB            Cloudinary
          |                   |
          v                   |
   Listings & Requests   Resource Images
          |
          v
   Borrowing Management
          |
          v
   Approval and Return
      Management

📂 Main Modules

1. Student Authentication
2. Resource Listing and Image Upload
3. Search and Category Filters
4. Borrowing Request Management
5. Approval and Rejection System
6. Return-Date Tracking
7. User Ratings and Reviews
8. Admin Moderation

⚙️ Installation and Setup

1. Clone the Repository

git clone YOUR_GITHUB_REPOSITORY_URL

2. Navigate to the Project

cd ShareSpace

3. Install Dependencies

Install frontend and backend dependencies separately:

cd frontend
npm install

cd ../backend
npm install

4. Configure Environment Variables

Create a ".env" file inside the backend directory.

PORT=5000
MONGODB_URI=your_mongodb_connection_string
JWT_SECRET=your_secret_key
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

Configure Firebase credentials separately according to your authentication implementation.

5. Run the Application

Start the backend:

cd backend
npm run dev

Start the frontend in another terminal:

cd frontend
npm run dev

🎯 Expected Outcome

A mini college marketplace where students can:

- Share unused academic resources.
- Borrow books and equipment.
- Save money through resource sharing.
- Connect with other students.
- Reduce unnecessary purchases.

🔮 Future Enhancements

- QR-code-based resource verification.
- AI-powered resource recommendations.
- College email verification.
- In-app messaging between students.
- Resource availability calendar.
- Mobile application.

👨‍💻 Project Information

Project Name: ShareSpace
Category: Full-Stack Web Development
Project Type: College Mini Project
Difficulty: Intermediate

📄 License

This project is developed for educational purposes.


⭐ If you like this project, give this repository a star!

Share More. Spend Less. Learn Together.
