package com.benjamin.nearly; // CHANGE to your app's package

/** Built-in message / question banks and mood definitions. Extend freely. */
final class NearlyContent {
    static final String[] LOVE = {
        "You are my favorite part of every day.",
        "Thinking of you and smiling.",
        "Home is wherever you are.",
        "I fall for you a little more each morning.",
        "You make ordinary days feel like magic.",
        "Thank you for being exactly you.",
        "My heart is yours, always.",
        "Every love song reminds me of you.",
        "I'm so proud of you today.",
        "You're my person. Forever.",
        "Missing you more than words can say.",
        "You turn my worries into warm hugs.",
        "I choose you, today and every day.",
        "Your laugh is my favorite sound.",
        "Together is my favorite place to be.",
        "You make my heart feel safe.",
        "I love the little things you do.",
        "Can't wait to see you again.",
        "You are loved. So very loved.",
        "Life is sweeter with you in it.",
        "Good morning, beautiful soul.",
        "Sweet dreams — I'll be dreaming of you.",
        "You're the best thing that ever happened to me.",
        "Every moment with you is a gift.",
        "I love you more than yesterday, less than tomorrow.",
        "You're my sunshine on cloudy days.",
        "Being with you feels like coming home.",
        "I'm grateful for you, always.",
        "You + me = my favorite story.",
        "Sending you the biggest hug."
    };

    static final String[] QUESTIONS = {
        "What's your favorite memory of us so far?",
        "What small thing do I do that makes you smile?",
        "Where would you take us for a dream weekend?",
        "What song reminds you of us?",
        "What are you most looking forward to together?",
        "What's one thing you've never told me you love about me?",
        "If we could live anywhere for a year, where?",
        "What was your first impression of me?",
        "What's your favorite way to spend a lazy day with me?",
        "What's a tradition you'd like us to start?",
        "What's something you're proud of this week?",
        "What meal reminds you of home?",
        "What's the best compliment you've ever received?",
        "What's a small adventure we should try soon?",
        "Which movie should we watch together next?",
        "What makes you feel most loved?",
        "What is one dream you'd like us to chase?",
        "What's your favorite photo of us and why?",
        "What would our perfect Sunday look like?",
        "What do you admire most about me?",
        "What's a skill you'd like us to learn together?",
        "What's your comfort food after a hard day?",
        "What's one thing I can do to support you this week?",
        "Describe our relationship in three words.",
        "What was the moment you knew?",
        "What's the silliest thing we've done together?",
        "Which place feels most like 'ours'?",
        "What would you say to us five years ago?",
        "What's one habit of mine you secretly adore?",
        "How can we make tomorrow a little better?"
    };

    static final String[] NHIE = {
        "Never have I ever stayed up all night talking to you.",
        "Never have I ever shown a stranger a photo of us.",
        "Never have I ever pretended to like a gift.",
        "Never have I ever read your messages twice just to smile.",
        "Never have I ever sung in the shower on purpose to impress you.",
        "Never have I ever cried at a movie we watched together.",
        "Never have I ever taken a secret photo of you.",
        "Never have I ever gotten lost on a trip.",
        "Never have I ever fallen asleep on a call.",
        "Never have I ever cooked something and lied about the recipe.",
        "Never have I ever practiced what to say before texting you.",
        "Never have I ever wanted to rewind a date and live it again.",
        "Never have I ever forgotten a plan we made.",
        "Never have I ever planned a surprise for you.",
        "Never have I ever laughed so hard I couldn't breathe because of you.",
        "Never have I ever kept a keepsake from one of our dates.",
        "Never have I ever stalked an old photo of you.",
        "Never have I ever told a friend something embarrassing about us.",
        "Never have I ever danced alone to our song.",
        "Never have I ever wished a day with you could be longer."
    };

    static final String[] CHALLENGES = {
        "Send each other a voice note saying 3 things you love.",
        "Take a selfie in matching poses and share it.",
        "Write a two-line poem about your partner.",
        "Plan a mini date for this weekend together.",
        "Share a song that describes your day.",
        "Give a 20-second hug when you meet next.",
        "Tell your partner one thing you're grateful for today.",
        "Swap a favorite photo of each other.",
        "Draw a quick doodle of your partner and send it.",
        "Share a childhood memory you've never told.",
        "Cook or order each other's favorite meal.",
        "Leave a surprise sticky note somewhere.",
        "Do a 10-minute walk or call with no phones otherwise.",
        "Write down 3 goals you want to reach together.",
        "Compliment your partner three times today.",
        "Recreate your first-date outfit.",
        "Share your favorite memory from this month.",
        "Pick a movie for tonight and watch it together.",
        "Teach each other one word in a new language.",
        "Send a 'good morning' video."
    };

    // ---- moods ----
    static final String[] MOOD_KEYS = {"happy", "loved", "excited", "calm", "silly", "tired", "sad", "stressed", "angry", "missing"};

    static String moodEmoji(String k) {
        if (k == null) return "😶";
        switch (k) {
            case "happy": return "😊";
            case "loved": return "🥰";
            case "excited": return "🤩";
            case "calm": return "😌";
            case "silly": return "😜";
            case "tired": return "😴";
            case "sad": return "😢";
            case "stressed": return "😣";
            case "angry": return "😠";
            case "missing": return "🥺";
            default: return "😶";
        }
    }

    static String moodLabel(String k) {
        if (k == null || k.isEmpty()) return "No mood yet";
        switch (k) {
            case "missing": return "Missing you";
            default: return Character.toUpperCase(k.charAt(0)) + k.substring(1);
        }
    }

    static String pick(String[] arr, long seed) {
        int i = (int) (((seed % arr.length) + arr.length) % arr.length);
        return arr[i];
    }
}
