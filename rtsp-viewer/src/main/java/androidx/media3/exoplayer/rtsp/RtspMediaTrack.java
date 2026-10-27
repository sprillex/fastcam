package androidx.media3.exoplayer.rtsp;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Util.castNonNull;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.ParserException;
import androidx.media3.common.util.CodecSpecificDataUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.ParsableBitArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.container.NalUnitUtil;
import androidx.media3.extractor.AacUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@UnstableApi
final class RtspMediaTrack {

  private static final String TAG = "RtspMediaTrack";

  private static final String PARAMETER_PROFILE_LEVEL_ID = "profile-level-id";
  private static final String PARAMETER_SPROP_PARAMS = "sprop-parameter-sets";
  private static final String PARAMETER_AMR_OCTET_ALIGN = "octet-align";
  private static final String PARAMETER_AMR_INTERLEAVING = "interleaving";
  private static final String PARAMETER_H265_SPROP_SPS = "sprop-sps";
  private static final String PARAMETER_H265_SPROP_PPS = "sprop-pps";
  private static final String PARAMETER_H265_SPROP_VPS = "sprop-vps";
  private static final String PARAMETER_H265_SPROP_MAX_DON_DIFF = "sprop-max-don-diff";
  private static final String PARAMETER_MP4A_CONFIG = "config";
  private static final String PARAMETER_MP4A_C_PRESENT = "cpresent";

  private static final String AAC_CODECS_PREFIX = "mp4a.40.";
  private static final String H264_CODECS_PREFIX = "avc1.";
  private static final String MPEG4_CODECS_PREFIX = "mp4v-es";

  private static final String GENERIC_CONTROL_ATTR = "*";

  private static final int DEFAULT_MP4V_WIDTH = 320;
  private static final int DEFAULT_MP4V_HEIGHT = 240;
  private static final int DEFAULT_VP8_WIDTH = 320;
  private static final int DEFAULT_VP8_HEIGHT = 240;
  private static final int OPUS_CLOCK_RATE = 48000;
  private static final int DEFAULT_VP9_WIDTH = 320;
  private static final int DEFAULT_VP9_HEIGHT = 240;
  private static final int DEFAULT_H263_WIDTH = 352;
  private static final int DEFAULT_H263_HEIGHT = 288;

  public final RtpPayloadFormat payloadFormat;
  public final Uri uri;

  public RtspMediaTrack(RtspHeaders headers, MediaDescription mediaDescription, Uri uri) {
    checkArgument(mediaDescription.attributes.containsKey("control"), "missing attribute control");
    this.payloadFormat = generatePayloadFormat(mediaDescription);
    this.uri = extractTrackUri(headers, uri, castNonNull(mediaDescription.attributes.get("control")));
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RtspMediaTrack that = (RtspMediaTrack) o;
    return payloadFormat.equals(that.payloadFormat) && uri.equals(that.uri);
  }

  @Override
  public int hashCode() {
    int result = 7;
    result = 31 * result + payloadFormat.hashCode();
    result = 31 * result + uri.hashCode();
    return result;
  }

  static RtpPayloadFormat generatePayloadFormat(MediaDescription mediaDescription) {
    Format.Builder formatBuilder = new Format.Builder();

    if (mediaDescription.bitrate > 0) {
      formatBuilder.setAverageBitrate(mediaDescription.bitrate);
    }

    int payloadType = mediaDescription.rtpMapAttribute.payloadType;
    String mediaEncoding = mediaDescription.rtpMapAttribute.mediaEncoding;
    String mimeType = RtpPayloadFormat.getMimeTypeFromRtpMediaType(mediaEncoding);
    formatBuilder.setSampleMimeType(mimeType);

    int clockRate = mediaDescription.rtpMapAttribute.clockRate;
    int channelCount = inferChannelCount(mediaDescription.rtpMapAttribute.encodingParameters, mimeType);

    if (MimeTypes.AUDIO_AC3.equals(mimeType)) {
      formatBuilder.setSampleRate(clockRate).setChannelCount(channelCount);
    }

    ImmutableMap<String, String> fmtpAttributes = mediaDescription.getFmtpParametersAsMap();

    switch (mimeType) {
      case MimeTypes.AUDIO_AAC:
        checkArgument(
            channelCount != -1, "AAC audio stream must include channel count in rtpmap attribute");
        checkArgument(
            !fmtpAttributes.isEmpty(), "AAC audio stream must include config fmtp parameter");
        String configString = fmtpAttributes.get(PARAMETER_MP4A_CONFIG);
        checkNotNull(configString, "AAC audio stream must include config fmtp parameter");
        checkArgument(
            configString.length() % 2 == 0, "Malformat MPEG4 config: " + configString);
        AacUtil.Config aacConfig = parseAacStreamMuxConfig(configString);
        formatBuilder
            .setSampleRate(aacConfig.sampleRateHz)
            .setChannelCount(aacConfig.channelCount)
            .setCodecs(aacConfig.codecs);
        processAacFmtpAttribute(
            formatBuilder, fmtpAttributes, mediaEncoding, channelCount, clockRate);
        break;

      case MimeTypes.AUDIO_AMR_NB:
      case MimeTypes.AUDIO_AMR_WB:
        checkArgument(channelCount == 1, "Multi channel AMR is not currently supported.");
        checkArgument(
            !fmtpAttributes.isEmpty(), "fmtp parameters must include octet-align.");
        checkArgument(
            fmtpAttributes.containsKey(PARAMETER_AMR_OCTET_ALIGN),
            "Only octet aligned mode is currently supported.");
        checkArgument(
            !fmtpAttributes.containsKey(PARAMETER_AMR_INTERLEAVING),
            "Interleaving mode is not currently supported.");
        break;

      case MimeTypes.AUDIO_OPUS:
        checkArgument(channelCount != -1);
        checkArgument(clockRate == OPUS_CLOCK_RATE, "Invalid OPUS clock rate.");
        break;

      case MimeTypes.VIDEO_MP4V:
        if (!fmtpAttributes.isEmpty()) {
          processMPEG4FmtpAttribute(formatBuilder, fmtpAttributes);
        } else {
          formatBuilder.setWidth(DEFAULT_MP4V_WIDTH).setHeight(DEFAULT_MP4V_HEIGHT);
        }
        break;

      case MimeTypes.VIDEO_H263:
        formatBuilder.setWidth(DEFAULT_H263_WIDTH).setHeight(DEFAULT_H263_HEIGHT);
        break;

      case MimeTypes.VIDEO_H264:
        processH264FmtpAttribute(formatBuilder, fmtpAttributes);
        break;

      case MimeTypes.VIDEO_H265:
        processH265FmtpAttribute(formatBuilder, fmtpAttributes);
        break;

      case MimeTypes.VIDEO_VP8:
        formatBuilder.setWidth(DEFAULT_VP8_WIDTH).setHeight(DEFAULT_VP8_HEIGHT);
        break;

      case MimeTypes.VIDEO_VP9:
        formatBuilder.setWidth(DEFAULT_VP9_WIDTH).setHeight(DEFAULT_VP9_HEIGHT);
        break;

      case MimeTypes.AUDIO_RAW:
      case MimeTypes.AUDIO_ALAW:
      case MimeTypes.AUDIO_MLAW:
        formatBuilder.setPcmEncoding(RtpPayloadFormat.getRawPcmEncodingType(mediaEncoding));
        break;

      default:
        break;
    }

    checkArgument(clockRate > 0);
    return new RtpPayloadFormat(
        formatBuilder.build(), payloadType, clockRate, fmtpAttributes, mediaEncoding);
  }

  private static int inferChannelCount(int encodingParameters, String mimeType) {
    if (encodingParameters != -1) {
      return encodingParameters;
    }
    if (MimeTypes.AUDIO_AC3.equals(mimeType)) {
      return 6;
    }
    return 1;
  }

  private static void processAacFmtpAttribute(
      Format.Builder formatBuilder,
      ImmutableMap<String, String> fmtpAttributes,
      String mediaEncoding,
      int channelCount,
      int sampleRate) {
    String profileLevelId = fmtpAttributes.get(PARAMETER_PROFILE_LEVEL_ID);
    if (profileLevelId == null && mediaEncoding.equals("MP4A-LATM")) {
      profileLevelId = "30";
    }
    checkArgument(
        profileLevelId != null && !profileLevelId.isEmpty(),
        "missing profile-level-id param");
    formatBuilder.setCodecs(AAC_CODECS_PREFIX + profileLevelId);
    formatBuilder.setInitializationData(
        ImmutableList.of(
            AacUtil.buildAacLcAudioSpecificConfig(sampleRate, channelCount)));
  }

  private static AacUtil.Config parseAacStreamMuxConfig(String streamMuxConfig) {
    ParsableBitArray bitArray = new ParsableBitArray(Util.getBytesFromHexString(streamMuxConfig));
    checkArgument(bitArray.readBits(1) == 0, "Only supports audio mux version 0.");
    checkArgument(bitArray.readBits(1) == 1, "Only supports allStreamsSameTimeFraming.");
    bitArray.skipBits(6);
    checkArgument(bitArray.readBits(4) == 0, "Only supports one program.");
    checkArgument(bitArray.readBits(3) == 0, "Only supports one numLayer.");
    try {
      return AacUtil.parseAudioSpecificConfig(bitArray, false);
    } catch (ParserException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static void processMPEG4FmtpAttribute(
      Format.Builder formatBuilder, ImmutableMap<String, String> fmtpAttributes) {
    String configString = fmtpAttributes.get(PARAMETER_MP4A_CONFIG);
    if (configString != null) {
      byte[] configBytes = Util.getBytesFromHexString(configString);
      formatBuilder.setInitializationData(ImmutableList.of(configBytes));
      android.util.Pair<Integer, Integer> resolution =
          CodecSpecificDataUtil.getVideoResolutionFromMpeg4VideoConfig(configBytes);
      formatBuilder.setWidth(resolution.first).setHeight(resolution.second);
    } else {
      formatBuilder.setWidth(DEFAULT_MP4V_WIDTH).setHeight(DEFAULT_MP4V_HEIGHT);
    }
  }

  private static byte[] getInitializationDataFromParameterSet(String parameterSet) {
    byte[] decoded = android.util.Base64.decode(parameterSet, android.util.Base64.DEFAULT);
    byte[] nalWithStartCode = new byte[decoded.length + NalUnitUtil.NAL_START_CODE.length];
    System.arraycopy(
        NalUnitUtil.NAL_START_CODE,
        0,
        nalWithStartCode,
        0,
        NalUnitUtil.NAL_START_CODE.length);
    System.arraycopy(
        decoded, 0, nalWithStartCode, NalUnitUtil.NAL_START_CODE.length, decoded.length);
    return nalWithStartCode;
  }

  private static void processH264FmtpAttribute(
      Format.Builder formatBuilder, ImmutableMap<String, String> fmtpAttributes) {
    if (!fmtpAttributes.containsKey(PARAMETER_SPROP_PARAMS)) {
      Log.w(TAG, "H264 fmtp attribute missing sprop-parameter-sets; relying on in-band NAL units.");
      return;
    }
    String spropParameterSets = checkNotNull(fmtpAttributes.get(PARAMETER_SPROP_PARAMS));
    String[] parameterSets = Util.split(spropParameterSets, ",");
    if (parameterSets.length != 2) {
      Log.w(TAG, "H264 fmtp attribute invalid sprop-parameter-sets: " + spropParameterSets);
      return;
    }
    byte[] sps = getInitializationDataFromParameterSet(parameterSets[0]);
    byte[] pps = getInitializationDataFromParameterSet(parameterSets[1]);
    ImmutableList<byte[]> initializationData = ImmutableList.of(sps, pps);
    formatBuilder.setInitializationData(initializationData);

    byte[] spsNalUnit = initializationData.get(0);
    NalUnitUtil.SpsData spsData =
        NalUnitUtil.parseSpsNalUnit(spsNalUnit, NalUnitUtil.NAL_START_CODE.length, spsNalUnit.length);
    formatBuilder
        .setPixelWidthHeightRatio(spsData.pixelWidthHeightRatio)
        .setHeight(spsData.height)
        .setWidth(spsData.width);
    if (fmtpAttributes.containsKey(PARAMETER_PROFILE_LEVEL_ID)) {
      String profileLevelId = checkNotNull(fmtpAttributes.get(PARAMETER_PROFILE_LEVEL_ID));
      formatBuilder.setCodecs(H264_CODECS_PREFIX + profileLevelId);
    } else {
      formatBuilder.setCodecs(
          CodecSpecificDataUtil.buildAvcCodecString(
              spsData.profileIdc, spsData.constraintsFlagsAndReservedZero2Bits, spsData.levelIdc));
    }
  }

  private static void processH265FmtpAttribute(
      Format.Builder formatBuilder, ImmutableMap<String, String> fmtpAttributes) {
    if (fmtpAttributes.containsKey(PARAMETER_H265_SPROP_MAX_DON_DIFF)) {
      int maxDonDiff =
          Integer.parseInt(checkNotNull(fmtpAttributes.get(PARAMETER_H265_SPROP_MAX_DON_DIFF)));
      checkArgument(
          maxDonDiff == 0, "non-zero sprop-max-don-diff " + maxDonDiff + " is not supported");
    }

    if (!fmtpAttributes.containsKey(PARAMETER_H265_SPROP_VPS)
        || !fmtpAttributes.containsKey(PARAMETER_H265_SPROP_SPS)
        || !fmtpAttributes.containsKey(PARAMETER_H265_SPROP_PPS)) {
      Log.w(TAG, "H265 fmtp attribute missing sprop-vps/sps/pps; relying on in-band NAL units.");
      return;
    }

    String vpsBase64 = checkNotNull(fmtpAttributes.get(PARAMETER_H265_SPROP_VPS));
    String spsBase64 = checkNotNull(fmtpAttributes.get(PARAMETER_H265_SPROP_SPS));
    String ppsBase64 = checkNotNull(fmtpAttributes.get(PARAMETER_H265_SPROP_PPS));

    byte[] vps = getInitializationDataFromParameterSet(vpsBase64);
    byte[] sps = getInitializationDataFromParameterSet(spsBase64);
    byte[] pps = getInitializationDataFromParameterSet(ppsBase64);

    ImmutableList<byte[]> initializationData = ImmutableList.of(vps, sps, pps);
    formatBuilder.setInitializationData(initializationData);

    byte[] spsNalUnit = initializationData.get(1);
    NalUnitUtil.H265SpsData spsData =
        NalUnitUtil.parseH265SpsNalUnit(spsNalUnit, NalUnitUtil.NAL_START_CODE.length, spsNalUnit.length);
    formatBuilder
        .setPixelWidthHeightRatio(spsData.pixelWidthHeightRatio)
        .setHeight(spsData.height)
        .setWidth(spsData.width)
        .setColorInfo(
            new ColorInfo.Builder()
                .setColorSpace(spsData.colorSpace)
                .setColorRange(spsData.colorRange)
                .setColorTransfer(spsData.colorTransfer)
                .setLumaBitdepth(spsData.bitDepthLumaMinus8 + 8)
                .setChromaBitdepth(spsData.bitDepthChromaMinus8 + 8)
                .build())
        .setCodecs(
            CodecSpecificDataUtil.buildHevcCodecString(
                spsData.generalProfileSpace,
                spsData.generalTierFlag,
                spsData.generalProfileIdc,
                spsData.generalProfileCompatibilityFlags,
                spsData.constraintBytes,
                spsData.generalLevelIdc));
  }

  private static Uri extractTrackUri(RtspHeaders headers, Uri sessionUri, String controlAttribute) {
    Uri controlUri = Uri.parse(controlAttribute);
    if (controlUri.isAbsolute()) {
      return controlUri;
    }

    Uri baseUri = sessionUri;
    String contentBase = headers.get("Content-Base");
    if (!TextUtils.isEmpty(contentBase)) {
      baseUri = Uri.parse(contentBase);
    } else {
      String contentLocation = headers.get("Content-Location");
      if (!TextUtils.isEmpty(contentLocation)) {
        baseUri = Uri.parse(contentLocation);
      }
    }

    if (GENERIC_CONTROL_ATTR.equals(controlAttribute)) {
      return baseUri;
    }
    return baseUri.buildUpon().appendEncodedPath(controlAttribute).build();
  }
}
