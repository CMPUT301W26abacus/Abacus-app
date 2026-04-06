const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendNotification = functions.https.onCall(async (data, context) => {

    const token = data.token;
    const title = data.title;
    const body = data.body;

    const message = {
        notification: {
            title: title,
            body: body
        },
        token: token
    };

    try {
        const response = await admin.messaging().send(message);
        return { success: true };
    } catch (error) {
        console.error(error);
        return { success: false };
    }
});