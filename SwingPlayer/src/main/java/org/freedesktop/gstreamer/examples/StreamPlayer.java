package org.freedesktop.gstreamer.examples;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.EnumSet;

import org.freedesktop.gstreamer.*;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.PlayBin;
import org.freedesktop.gstreamer.event.SeekFlags;
import org.freedesktop.gstreamer.swing.GstVideoComponent;

import javax.imageio.ImageIO;

public class StreamPlayer extends Thread {

    private PlayBin playbin = null;
    private AppSink appSink = null;
    private URI source = null;

    public StreamPlayer(URI source) {
        Utils.configurePaths();
        Gst.init(Version.BASELINE, "StreamPlayer", new String[0]);
        this.source = source;
        final GstVideoComponent vc = new GstVideoComponent();
        appSink = ((AppSink)vc.getElement());
        appSink.set("emit-signals", false);
        appSink.set("drop", true);
        appSink.set("max-buffers", 5);
        appSink.set("wait-on-eos", true);
        playbin = new PlayBin("play-bin");
        playbin.setVideoSink(appSink);
        playbin.getBus().connect((Bus.EOS) src -> {
            System.out.println("Stream ended.");
            retry();
        });
        playbin.getBus().connect(new Bus.ERROR() {
            @Override
            public void errorMessage(GstObject source, int code, String message) {
                System.out.println("Error: " + message);
                retry();
            }
        });
    }

    private void retry(){
        try {
            Thread.sleep(5000);
            playbin.seekSimple(Format.TIME,EnumSet.of(SeekFlags.FLUSH),0);
            playbin.stop();
            playbin.play();
        } catch (Exception e) {
        }
    }
    public URI getSource() {
        return source;
    }

    public void setSource(URI source) {
        this.source = source;
    }

    @Override
    public void start() {
        playbin.stop();
        playbin.setURI(source);
        playbin.play();
    }

    public void dispose() {
        playbin.stop();
    }

    private boolean isPlaying(){
        if(appSink.getState()== org.freedesktop.gstreamer.State.PLAYING){
            return true;
        }
        return false;
    }

    public BufferedImage pullFrame() {
        if (!appSink.isEOS() && isPlaying()) {
            Sample sample = null;
            Buffer buffer = null;
            try {
                System.out.println("Pulling new frame...");
                long startTime = System.currentTimeMillis();
                sample = appSink.pullSample();
                Caps caps = sample.getCaps();
                Structure struct = caps.getStructure(0);
                int width = struct.getInteger("width");
                int height = struct.getInteger("height");
                buffer = sample.getBuffer();
                ByteBuffer bb = buffer.map(false);
                BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
                bb.asIntBuffer().get(pixels, 0, width * height);
                //System.out.println("Pulled in " + (System.currentTimeMillis() - startTime) + "...");
                return image;
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                if (sample != null)
                    sample.dispose();
                if (buffer != null)
                    buffer.unmap();
            }
        }
        return null;
    }

    public static void main(String[] args) {
        StreamPlayer sp = new StreamPlayer(URI.create("rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mp4"));
        sp.start();
        new Thread(()->{
            long count = 0;
            long startTime = System.currentTimeMillis();
            while(true){
                try {
                    BufferedImage image = sp.pullFrame();
                    if(image!=null) {
                        count++;
                        System.out.println((1000*count/ (System.currentTimeMillis() - startTime)) + " fps");
                    }
                    //    ImageIO.write(image, "jpg", new File("C:\\Users\\MOHAMMAD\\Desktop\\New folder (13)\\" + System.currentTimeMillis() + ".jpg"));
                    //Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}

