import LeafMark from "./LeafMark";

const AuthBrandPanel = ({ headline, body, features }) => {
  return (
    <div
      className="relative hidden flex-1 flex-col overflow-hidden px-12 py-12 text-[#efe9d8] lg:flex xl:px-14"
      style={{
        background:
          "linear-gradient(165deg, var(--color-sidebar) 0%, #1b2548 58%, var(--color-tag-teal) 150%)",
      }}
    >
      <div
        className="pointer-events-none absolute -right-20 -top-24 h-[420px] w-[420px] rounded-full"
        style={{
          background:
            "radial-gradient(circle, rgba(217,171,92,.16), transparent 70%)",
        }}
      />

      <div className="relative flex items-center gap-2.5">
        <LeafMark className="h-8 w-8 text-white" />
        <div>
          <div className="font-display text-[22px] font-semibold text-white">
            Sakha
          </div>
          <div className="mt-0.5 text-xs text-[#b9c0dd]">Your AI companion</div>
        </div>
      </div>

      <div className="relative mt-auto pt-14">
        <div className="mb-4 h-[3px] w-11 rounded-full bg-accent" />
        <h1 className="max-w-[11ch] font-display text-[34px] font-semibold leading-[1.12] tracking-tight text-white xl:text-[40px]">
          {headline}
        </h1>
        <p className="mt-3.5 max-w-[46ch] text-[14px] leading-relaxed text-[#c7cce4]">
          {body}
        </p>
      </div>

      <div className="relative mt-7 flex flex-col gap-3.5">
        {features.map(({ icon: Icon, title, body: featureBody }) => (
          <div key={title} className="flex items-start gap-3">
            <div className="flex h-8 w-8 flex-none items-center justify-center rounded-[9px] bg-white/[0.08] text-accent">
              <Icon className="h-[15px] w-[15px]" />
            </div>
            <div>
              <div className="text-[13.5px] font-semibold text-white">{title}</div>
              <div className="mt-0.5 text-[12.5px] leading-snug text-[#a9b0d1]">
                {featureBody}
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="relative mt-auto border-t border-white/[0.14] pt-7">
        <div className="text-[15px] text-white">&#2360;&#2326;&#2366; &mdash; sakha</div>
        <div className="mt-0.5 text-[12.5px] text-[#a9b0d1]">
          Sanskrit: a friend, companion, or guide.
        </div>
      </div>
    </div>
  );
};

export default AuthBrandPanel;
