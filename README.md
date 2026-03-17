Real-Time Mobile Denoising (MFDNet) 📱


A highly optimized, mobile-first neural network designed to remove image noise in real-time. Built specifically for Android, this project achieves sub-100ms inference on modern smartphone CPUs by leveraging a custom, lightweight architecture (MFDNet).


🚀 Key Features:


Blazing Fast Mobile Inference: Designed to run cleanly on edge devices. By relying heavily on depthwise separable convolutions, the model maintains a very small memory footprint.

Blind Quality Assessment (Total Variation): Evaluating image quality in the real world is hard because you don't have a "clean" ground-truth image to compare against. To solve this, we implemented a custom Total Variation (TV) metric that evaluates the noisiness and smoothness of an image blindly.

Multi-Scale Fusion: Captures a wide variety of receptive fields to understand context, ensuring high-frequency textures aren't blurred out along with the noise.


🛠️ Tech Stack:


Model Training: PyTorch, Jupyter Notebook

Mobile Deployment: TensorFlow Lite (TFLite)

Android App: Kotlin, Java, Android Studio

Metrics: PSNR, SSIM, custom Total Variation (TV)


📊 The Total Variation (TV) Metric Explained:


How do you know if an image looks good if you don't have the original?

Standard metrics like PSNR and SSIM require a perfect ground-truth reference image. Our pipeline integrates a custom calculation for the Total Variation (TV) metric. TV measures the sum of absolute differences between adjacent pixels. High noise creates huge differences between neighboring pixels (high TV). A clean, denoised image has smooth transitions (low TV). By minimizing the TV score without blurring edges, we can score real-world denoising performance blindly.


🧠 Architecture Highlights:


MFDNet (Mobile Feature Distillation Network): 32 base channels with 4 progressive feature distillation blocks.

SepConv (Separable Convolutions): The backbone of our efficiency, drastically cutting parameters compared to standard convolutions.

Residual Learning: The network doesn't predict the clean image outright; it predicts the noise (the residual) and subtracts it from the original input. This makes learning much easier and faster.


⚡ Quick Start (Android):


Grab the latest app-release.apk from the Releases page.

Install it on your Android device.

Select any noisy image from your gallery and watch it denoise instantly.


📂 Repository Map:


DenoiseAndroid/: The complete Android Studio project containing all Kotlin/Java source code and UI layouts.

mfdnet.py: The PyTorch architecture definition. Highly documented.

validate.py: The evaluation loop used to score the model (PSNR, SSIM, TV).

MFDNet.tflite: Core exported model weights optimized for mobile.


👾 Authors:


Shaik Abdul Latif (Lead)

Sharaj G S (Co-Author)

Minor documentation update by Sharaj G S


🤝 Community & Contributions:


We are open to collaboration! If you’d like to improve the model or the app's performance, feel free to check out our Issues or submit a Pull Request.
