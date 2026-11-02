import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Run from the repository root with JDK 17: java tools/PrepareBrandAssets.java */
public class PrepareBrandAssets {
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args.length == 0
            ? "app/src/img/e36b1910-1e8f-45fd-bd5e-ff088e2a22c4.png" : args[0]);
        BufferedImage original = ImageIO.read(input.toFile());
        if (original.getWidth() != 1254 || original.getHeight() != 1254) {
            throw new IllegalArgumentException("Crop coordinates expect the supplied 1254px original");
        }
        Path output = Path.of("app/src/main/res/drawable-nodpi");
        Files.createDirectories(output);
        // Preserve the original; remove only the near-black backdrop in derived copies.
        BufferedImage logo = transparentCrop(original, 175, 180, 910, 800);
        BufferedImage symbol = transparentCrop(original, 290, 185, 675, 600);
        save(scale(logo, 640, 563), output.resolve("pyte_logo.png"));
        save(scale(symbol, 320, 284), output.resolve("pyte_symbol.png"));
        save(scale(transparentCrop(original, 195, 795, 875, 175), 500, 100), output.resolve("pyte_wordmark.png"));
        BufferedImage icon = new BufferedImage(432, 432, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = icon.createGraphics();
        canvas.setComposite(AlphaComposite.SrcOver);
        canvas.drawImage(scale(symbol, 260, 231), 86, 100, null);
        canvas.dispose();
        save(icon, output.resolve("pyte_launcher_foreground.png"));
    }

    private static BufferedImage transparentCrop(BufferedImage source, int x, int y, int w, int h) {
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) {
            int rgb = source.getRGB(x + i, y + j);
            int maximum = Math.max((rgb >> 16) & 255, Math.max((rgb >> 8) & 255, rgb & 255));
            int alpha = maximum <= 12 ? 0 : maximum >= 32 ? 255 : (maximum - 12) * 255 / 20;
            result.setRGB(i, j, (alpha << 24) | (rgb & 0xFFFFFF));
        }
        return result;
    }

    private static BufferedImage scale(BufferedImage source, int w, int h) {
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = result.createGraphics();
        canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        canvas.drawImage(source, 0, 0, w, h, null);
        canvas.dispose();
        return result;
    }

    private static void save(BufferedImage image, Path path) throws Exception {
        ImageIO.write(image, "png", path.toFile());
    }
}
