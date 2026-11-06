# Legal check (copyright, licenses, trademarks)

Status as of 2026-10-24. This is an engineering review, not legal advice. Before the app is
sold, have a lawyer confirm the items marked **lawyer**.

## Everything the app ships or downloads

| Component | License | Commercial use | What we must do |
|---|---|---|---|
| TripoSR model | MIT (Tripo AI & Stability AI) | Yes | Keep the notice (done: Settings → open-source licenses) |
| TripoSG weights | MIT (VAST-AI-Research) | Yes | Notice (done) |
| TripoSG model *code* | Partly Tencent Hunyuan Community License | **Not used any more** (pack `triposg-v2` on): CI loads the MIT weights into our own implementation, `convert/sg_model.py` | Pack `triposg-v1` was converted with the original code; retire it when the app switches to v2 |
| DINOv2 (inside TripoSG) | Apache-2.0 (Meta) | Yes | Notice + license text (done) |
| U-2-Net u2netp, ISNet | Apache-2.0 | Yes | Done |
| Real-ESRGAN x4v3 | BSD-3-Clause | Yes | Done |
| MobileSAM, Depth Anything V2 **Small** | Apache-2.0 | Yes | Done. Note: Depth Anything V2 Base/Large are CC-BY-NC, never use them |
| ONNX Runtime | MIT | Yes | Done |
| Qualcomm QNN runtime | Qualcomm AI Model & Software License | Yes, only as object code inside our app | Never offer the QNN libraries as a separate download; don't reverse engineer them; don't use for the listed prohibited uses (none apply to us) |
| ZXing, AndroidX, Compose, Kotlin | Apache-2.0 | Yes | Done |
| Rubik font (keychain/stamp text) | SIL OFL 1.1 | Yes, including printed products | Done. The phone's system font is no longer used for printed text (on Samsung it is Samsung's own font) |

Models we checked and rejected: RMBG (non-commercial), diso (CC BY-NC), pymeshlab (GPL),
Depth Anything V2 Base/Large (CC-BY-NC), FLUX.2 klein 9B (non-commercial), FLUX 3 Klein
(revenue cap), SF3D / SPAR3D (Stability community license, revenue cap).

## Risks found

1. **App name "TripoSR 3D" (high).** "Tripo" is the product brand of VAST (Tripo AI, Tripo
   Studio). Using it as our app's name invites a trademark complaint and Google Play rejects
   apps that look like another company's product. MIT lets us use the code, not the name.
   Rename the app and the package (`com.triposr.mobile`) before publishing; keep "uses
   TripoSR (MIT)" only in the credits.
2. **TripoSG / PartCrafter and Tencent Hunyuan (medium).** See the table. Until a lawyer says
   otherwise: don't sell the TripoSG pack in the EU, UK or South Korea, or replace the
   conversion code with our own implementation. Step1X-3D (Apache-2.0) has no such issue.
3. **What users create (medium).** Users can make 3D models or keychains of characters, logos
   or brands they don't own. **Done:** Terms of Use (`TERMS.md`, in the app and accepted on first
   launch) put that on the user and warn about food-contact prints. Still: never advertise it
   ("print your favourite Marvel hero"), and add a takedown process if we ever host user models.
4. **Privacy (low, required for Google Play).** **Done:** privacy policy (`PRIVACY.md`, Hebrew +
   English, also in the app). Missing: contact e-mail and a public URL (see `PLAY_RELEASE.md`).
5. **QR Wi-Fi plates.** The password is inside the printed code; the app warns about it.

## Model outputs

TripoSR and TripoSG outputs (meshes) may be sold: MIT puts no restriction on outputs.
Lithophanes, reliefs, QR plates, keychains, cookie cutters and stamps are made by our own
code from the user's image or text.

## Own TripoSG implementation (2026-10-25)

`convert/sg_model.py` is written for this project: a plain PyTorch denoiser and occupancy
decoder whose parameter names match the published checkpoint. CI no longer downloads or runs
the TripoSG repository (only the MIT weights from Hugging Face and one example picture).
Checked: on the same (random) weights it gives bit-identical outputs to the original code for
the denoiser at three timesteps and for the decoder, and a deliberately introduced bug is
caught (relative error 0.35). All exported graphs match PyTorch to < 1e-6.

What remains for the lawyer is narrower: whether the TripoSG *weights* themselves (trained by
VAST with Hunyuan-derived code, released under MIT) carry any Hunyuan obligation. VAST
publishes them as MIT, and the Hunyuan license says Tencent claims no rights in outputs.

## Written answer from Tripo / VAST support (2026-11-06)

Ticket #20261025000338 (support@ Tripo, agent "Cherry"), reply of 2026-11-06 to our e-mail of
2026-10-25 describing exactly this use (MIT weights from Hugging Face, own implementation,
converted for the Qualcomm NPU, hosted for download, app sold later, MIT notice + credit):

1. "Commercial Use: The MIT License permits commercial use, modification, and redistribution of
   the licensed materials, subject to its terms and conditions."
2. "Additional License Requirements: Based on the publicly available license information, we did
   not find any indication that the TripoSG weights are additionally subject to the Tencent
   Hunyuan Community License."

They closed with: "We appreciate your interest in integrating TripoSG into Makeo 3D and wish you
success with your project!" Note the wording: a review of the public license, not a separate
signed grant. It strongly supports risk 2 being low. The e-mail is kept as
`docs/legal-evidence/2026-11-06_tripo-vast_triposg-commercial-use.pdf`.

## Converting models in CI (question from 2026-10-24)

Converting TripoSG on the build server does not copy anyone's code into the app: the workflow
downloads the MIT weights, runs the model code once to trace the computation, and ships only the
resulting ONNX/QNN files (weights + graph). The open question is the same as risk 2: whether the
traced graph counts as a derivative of the Hunyuan-licensed model code. If the lawyer says it
does, the fix is to write our own model definition that loads the same weights, so the CI no
longer runs Tencent code. The conversion tools themselves (PyTorch BSD-3, ONNX Apache-2.0,
ONNX Runtime MIT, Qualcomm QAIRT under its license for compiling) allow this use.

## Candidate models checked on 2026-10-24

| Model | License | Depends on | Phone NPU? | Verdict |
|---|---|---|---|---|
| TRELLIS.2 (Microsoft, 4B) | MIT code and weights | DINOv3 image encoder (custom Meta license: commercial OK, no military/ITAR use, pass the terms on); nvdiffrast/nvdiffrec (NVIDIA, **non-commercial**) for rendering and PBR baking, which can be replaced; CuMesh/FlexGEMM sparse-voxel CUDA kernels | No: sparse 3D convolutions/attention have no QNN equivalent, 4B params ≈ 8 GB fp16, needs a 24 GB GPU | Best quality, not portable now. Revisit if a dense variant appears |
| Pixal3D (Tencent ARC) | Repo and model card say MIT; some secondary sources claim "academic only, not in the EU" | Built on TRELLIS.2 (same dependencies) and Direct3D-S2; read its NOTICE file | No, same sparse-voxel design, ~1.3B per stage | Wait for a clear license; ask the lawyer |
| UniTEX (texturing) | Code Apache-2.0 | LoRA on a FLUX model (FLUX.1-dev is **non-commercial**); optional super-resolution on SD3-Medium (Stability community license, revenue cap); its own checkpoints not released yet | Not as published (FLUX 12B) | Not usable commercially as is. Would need retraining on an Apache base such as FLUX.2 klein 4B |
| FLUX.2 klein 4B | Apache-2.0 | – | Plausible on the NPU, 4B int8 ≈ 4 GB | Usable for text-to-image → 3D later |

**Texture for TripoSG without new licenses:** color the TripoSG mesh by querying TripoSR's color
field (already on the phone) at the TripoSG vertices, after aligning the two coordinate frames.
This adds a few seconds and no new model.

## Second-opinion review (2026-10-25) and what changed

An outside AI review agreed the main open risk is the TripoSG **weights** (distribution rights,
especially in the EU), not our code. Also noted: the original TripoSG repo contains FlashVDM-derived
code (Tencent Hunyuan FlashVDM license) besides HunyuanDiT. Neither is used now: `sg_model.py`
implements only the plain decoder (no flash/hierarchical decoder code), and the app's
coarse-to-fine extraction is our own Kotlin.

Changes made:
- **TripoSG is not offered in the EU/EEA, the UK and South Korea** (country from SIM / network,
  `Region` in Services.kt) until the lawyer clears the weights. TripoSR (MIT, no Tencent code)
  stays the default everywhere, so the app works fully without TripoSG.
- Terms v2 (users re-accept): no license to third-party characters/brands is granted; we may
  restrict infringing use; generated files are not a safety or regulatory approval (food contact);
  infringement reports answered within 14 days.
- Cookie cutter and stamp screens say the file is not a food-safety approval.
- Still for professionals: official trademark search for "Makeo 3D" (Israel, USPTO, EUIPO);
  DSA trader status before launch; accountant on when to open a business file.
