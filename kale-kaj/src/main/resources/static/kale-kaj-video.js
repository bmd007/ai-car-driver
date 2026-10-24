function startKaleKajVideo(video, baseUrl) {
    const socketUrl = new URL('v3/h264', baseUrl);
    socketUrl.protocol = socketUrl.protocol === 'https:' ? 'wss:' : 'ws:';

    function connect() {
        const jmuxer = new JMuxer({node: video, mode: 'video', flushingTime: 0, maxDelay: 300, fps: 15, debug: false});
        const socket = new WebSocket(socketUrl);
        socket.binaryType = 'arraybuffer';
        socket.onmessage = event => jmuxer.feed({video: new Uint8Array(event.data)});
        socket.onclose = () => {
            jmuxer.destroy();
            setTimeout(connect, 1000);
        };
        return socket;
    }

    function keepLive() {
        if (video.buffered.length > 0) {
            const liveEdge = video.buffered.end(video.buffered.length - 1);
            if (liveEdge - video.currentTime > 0.5) {
                video.currentTime = liveEdge - 0.1;
            }
        }
    }

    let socket = connect();
    setInterval(keepLive, 1000);
    return {
        restart: () => socket.close()
    };
}
